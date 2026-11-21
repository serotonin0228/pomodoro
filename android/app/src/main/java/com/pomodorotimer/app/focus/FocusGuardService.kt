package com.pomodorotimer.app.focus

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.pomodorotimer.app.MainActivity

/** Resolves the foreground package from window metadata without traversing accessibility nodes. */
class FocusGuardService : AccessibilityService() {
  companion object {
    private const val REDIRECT_RETRY_MS = 900L
    @Volatile var connected = false
  }
  private val handler = Handler(Looper.getMainLooper())
  private var launchable: Set<String> = emptySet()
  private var essential: Set<String> = emptySet()
  private var cacheAt = -60000L
  private var foreground = ""
  private var observedAt = 0L
  private var observedWall = 0L
  private var usageAt = 0L
  private var redirectedAt = -REDIRECT_RETRY_MS
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
          refreshForeground()
          enforce()
        }
      } catch (e: Exception) { FocusGuardDiagnostics.lastError = e.javaClass.simpleName }
      handler.postDelayed(this, if (interactive()) 400 else 1_000)
    }
  }
  override fun onServiceConnected() {
    super.onServiceConnected()
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
  private fun foregroundApplication(): String? {
    val currentWindows = windows
    val app = currentWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
      ?: currentWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
    return app?.root?.packageName?.toString()?.takeIf { it.isNotBlank() }
  }

  private fun refreshForeground() {
    val pkg = foregroundApplication()
    if (pkg != null) {
      observe(pkg)
      return
    }
    FocusGuardUsage.latest(this, usageAt, System.currentTimeMillis())?.let {
      usageAt = it.at
      if (it.at > observedWall) observe(it.packageName, it.at)
    }
  }

  private fun observe(pkg: String, at: Long = System.currentTimeMillis()) {
    if (pkg.isBlank()) return
    if (foreground != pkg) {
      foreground = pkg
      redirectedAt = -REDIRECT_RETRY_MS
      homeFallback?.let { handler.removeCallbacks(it) }
      homeFallback = null
    }
    observedAt = SystemClock.elapsedRealtime()
    observedWall = at
    FocusGuardDiagnostics.lastSeen = pkg
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val type = event?.eventType ?: return
    if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
    if (!FocusGuardStore.isActive(this) || !interactive()) return
    FocusGuardDiagnostics.events++
    try {
      val pkg = foregroundApplication()
        ?: event.packageName?.toString()?.takeIf { type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
        ?: return
      observe(pkg)
      enforce()
    } catch (e: Exception) {
      FocusGuardDiagnostics.lastError = e.javaClass.simpleName
    }
  }

  private fun isRestricted(pkg: String): Boolean = FocusGuardPolicy.shouldBlock(
    pkg,
    preferences.getString("mode", "blacklist") ?: "blacklist",
    preferences.getStringSet("blocked", emptySet()) ?: emptySet(),
    preferences.getStringSet("allowed", emptySet()) ?: emptySet(),
    launchable,
    essential,
  )

  private fun enforce() {
    val now = SystemClock.elapsedRealtime()
    if (!FocusGuardStore.isActive(this) || !interactive() || now - observedAt > 3000 || now - redirectedAt < REDIRECT_RETRY_MS) return
    if (now - cacheAt > 30000) refreshApps()
    if (!isRestricted(foreground)) return
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
      if (!FocusGuardStore.isActive(this) || !interactive()) return@Runnable
      try {
        val pkg = foregroundApplication() ?: foreground
        if (pkg == restrictedPackage && isRestricted(pkg)) {
          FocusGuardDiagnostics.lastHome = performGlobalAction(GLOBAL_ACTION_HOME)
        }
      } catch (e: Exception) {
        FocusGuardDiagnostics.lastError = e.javaClass.simpleName
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
