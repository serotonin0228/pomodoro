package com.pomodoro.focus

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
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
  private var launchPending: Runnable? = null
  private val ticker = object : Runnable {
    override fun run() {
      try {
        if (FocusGuardStore.isActive(this@FocusGuardService) && interactive()) {
          val now = System.currentTimeMillis()
          FocusGuardUsage.latest(this@FocusGuardService, usageAt, now)?.let {
            usageAt = it.at
            if (it.at > observedWall) observe(it.packageName, it.at)
          }
          enforce()
        }
      } catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
      handler.postDelayed(this, 400)
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
    handler.removeCallbacks(ticker); handler.post(ticker)
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
    FocusGuardDiagnostics.lastMatched = foreground
    FocusGuardDiagnostics.lastError = ""
    // Android can silently deny bringing a background Activity to the front. HOME first ensures
    // the restricted app is left even on such devices; launch Pomodoro after the transition.
    FocusGuardDiagnostics.lastHome = performGlobalAction(GLOBAL_ACTION_HOME)
    val launch = Runnable {
      launchPending = null
      if (!FocusGuardStore.isActive(this) || !interactive()) return@Runnable
      try {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        FocusGuardDiagnostics.returns++
      } catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
    }
    launchPending?.let { handler.removeCallbacks(it) }
    launchPending = launch
    handler.postDelayed(launch, 180)
  }
  override fun onInterrupt() { }
  override fun onDestroy() { handler.removeCallbacksAndMessages(null); connected = false; super.onDestroy() }
}
