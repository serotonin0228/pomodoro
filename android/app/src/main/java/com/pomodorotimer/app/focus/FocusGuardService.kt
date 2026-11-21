package com.pomodorotimer.app.focus

import android.accessibilityservice.AccessibilityService
import com.pomodorotimer.app.MainActivity
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** Uses package names only. No view tree, screen text, screenshots, gestures, or network. */
class FocusGuardService : AccessibilityService() {
  companion object { @Volatile var connected = false }
  private val handler = Handler(Looper.getMainLooper())
  private var launchable: Set<String> = emptySet()
  private var essential: Set<String> = emptySet()
  private var cacheAt = -60000L
  private var foreground = ""
  private var observedAt = 0L
  private var observedWall = 0L
  private var usageAt = 0L
  private var redirectedAt = -2000L
  private var homeFallback: Runnable? = null
  private val preferences by lazy { FocusGuardStore.prefs(this) }
  private val focusChanges = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == "status" || key == "enabled" || key == "deadline" || key == "phase" || key == null) restartTicker()
  }
  private val ticker = object : Runnable {
    override fun run() {
      if (!FocusGuardStore.isActive(this@FocusGuardService)) return
      try {
        if (interactive()) {
          val now = System.currentTimeMillis()
          FocusGuardUsage.latest(this@FocusGuardService, usageAt, now)?.let {
            usageAt = it.at
            if (it.at > observedWall) observe(it.packageName, it.at)
          }
          enforce()
        }
      } catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
      handler.postDelayed(this, if (interactive()) 400 else 1_000)
    }
  }
  override fun onServiceConnected() {
    super.onServiceConnected()
    serviceInfo = serviceInfo.apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_FOCUSED
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      notificationTimeout = 50
    }
    connected = true
    try { refreshApps() } catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
    preferences.registerOnSharedPreferenceChangeListener(focusChanges)
    restartTicker()
  }
  private fun restartTicker() {
    handler.removeCallbacks(ticker)
    if (connected && FocusGuardStore.isActive(this)) handler.post(ticker)
  }
  private fun refreshApps() {
    essential = FocusGuardApps.essential(this)
    launchable = packageManager.queryIntentActivities(FocusGuardApps.launcherIntent(), 0).map { it.activityInfo.packageName }.toSet()
    cacheAt = SystemClock.elapsedRealtime()
  }
  private fun interactive() = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
  private fun observe(pkg: String, at: Long = System.currentTimeMillis()) {
    if (pkg.isBlank()) return
    foreground = pkg; observedAt = SystemClock.elapsedRealtime(); observedWall = at
    FocusGuardDiagnostics.lastSeen = pkg
    if (pkg == packageName) {
      homeFallback?.let { handler.removeCallbacks(it) }
      homeFallback = null
    }
  }
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || event.eventType !in listOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_VIEW_FOCUSED)) return
    FocusGuardDiagnostics.events++
    try { observe(event.packageName?.toString() ?: return); enforce() }
    catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
  }
  private fun enforce() {
    val now = SystemClock.elapsedRealtime()
    if (!FocusGuardStore.isActive(this) || !interactive() || now - observedAt > 3000 || now - redirectedAt < 900) return
    if (now - cacheAt > 30000) refreshApps()
    val p = FocusGuardStore.prefs(this)
    if (!FocusGuardPolicy.shouldBlock(foreground, p.getString("mode", "blacklist") ?: "blacklist",
        p.getStringSet("blocked", emptySet()) ?: emptySet(), p.getStringSet("allowed", emptySet()) ?: emptySet(), launchable, essential)) return
    redirectedAt = now
    val restrictedPackage = foreground
    FocusGuardDiagnostics.lastMatched = restrictedPackage
    FocusGuardDiagnostics.lastError = ""
    FocusGuardDiagnostics.lastHome = false

    // A successful launch takes the user straight to the timer. If the system leaves the
    // restricted app in front, HOME still lets the accessibility service leave that app.
    try {
      startActivity(
        Intent(this, MainActivity::class.java)
          .setAction(MainActivity.ACTION_RETURN_TO_TIMER)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      )
    } catch (e: Exception) {
      FocusGuardDiagnostics.lastError = e.javaClass.simpleName
    }

    val fallback = Runnable {
      homeFallback = null
      if (foreground == restrictedPackage && FocusGuardStore.isActive(this) && interactive()) {
        FocusGuardDiagnostics.lastHome = performGlobalAction(GLOBAL_ACTION_HOME)
      }
    }
    homeFallback?.let { handler.removeCallbacks(it) }
    homeFallback = fallback
    handler.postDelayed(fallback, 650)
  }
  override fun onInterrupt() { }
  override fun onDestroy() {
    preferences.unregisterOnSharedPreferenceChangeListener(focusChanges)
    handler.removeCallbacksAndMessages(null)
    connected = false
    super.onDestroy()
  }
}
