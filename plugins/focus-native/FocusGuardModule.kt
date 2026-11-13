package com.pomodoro.focus

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.facebook.react.bridge.*

class FocusGuardModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
  override fun getName() = "FocusGuard"
  private fun config(): WritableMap {
    val p = FocusGuardStore.prefs(ctx)
    val enabled = ctx.getSystemService(AccessibilityManager::class.java)
      .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
      .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName && it.resolveInfo.serviceInfo.name == FocusGuardService::class.java.name }
    return Arguments.createMap().apply {
      putBoolean("enabled", p.getBoolean("enabled", false)); putString("mode", p.getString("mode", "blacklist"))
      for (key in listOf("blocked", "allowed")) putArray(key, Arguments.createArray().apply { for (value in p.getStringSet(key, emptySet()) ?: emptySet()) pushString(value) })
      putBoolean("permission", enabled); putBoolean("connected", FocusGuardService.connected)
      putBoolean("active", FocusGuardStore.isActive(ctx)); putBoolean("usage", FocusGuardUsage.allowed(ctx))
      putString("sessionStatus", p.getString("status", "idle")); putBoolean("bootReadable", FocusGuardStore.boot(ctx) >= 0)
      putInt("events", FocusGuardDiagnostics.events); putString("lastSeen", FocusGuardDiagnostics.lastSeen)
      putString("lastMatched", FocusGuardDiagnostics.lastMatched); putString("lastError", FocusGuardDiagnostics.lastError)
      putBoolean("lastHome", FocusGuardDiagnostics.lastHome); putInt("returns", FocusGuardDiagnostics.returns)
    }
  }
  @ReactMethod fun getConfig(promise: Promise) { try { promise.resolve(config()) } catch (e: Exception) { promise.reject("GUARD_CONFIG", e) } }
  @ReactMethod fun configure(value: ReadableMap, promise: Promise) {
    try {
      val mode = value.getString("mode")
      require(mode == "blacklist" || mode == "whitelist")
      val edit = FocusGuardStore.prefs(ctx).edit().putBoolean("enabled", value.getBoolean("enabled")).putString("mode", mode)
      for (key in listOf("blocked", "allowed")) {
        val values = value.getArray(key) ?: Arguments.createArray()
        val packages = (0 until values.size()).mapNotNull { values.getString(it) }.filter { it.length <= 255 && it.matches(Regex("[A-Za-z0-9_.]+")) }.toSet()
        edit.putStringSet(key, packages)
      }
      edit.apply(); promise.resolve(config())
    } catch (e: Exception) { promise.reject("GUARD_CONFIG", e) }
  }
  @ReactMethod fun listApps(promise: Promise) {
    try {
      val pm = ctx.packageManager; val essential = FocusGuardApps.essential(ctx)
      val apps = pm.queryIntentActivities(FocusGuardApps.launcherIntent(), 0).distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName !in essential }.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.sortedBy { it.second.lowercase() }
      promise.resolve(Arguments.createArray().apply { for ((pkg, label) in apps) pushMap(Arguments.createMap().apply { putString("packageName", pkg); putString("label", label) }) })
    } catch (e: Exception) { promise.reject("GUARD_APPS", e) }
  }
  @ReactMethod fun openPermission(promise: Promise) {
    try {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(":settings:fragment_args_key", ComponentName(ctx, FocusGuardService::class.java).flattenToString())
      ctx.startActivity(intent); promise.resolve(null)
    } catch (e: Exception) { promise.reject("GUARD_PERMISSION", e) }
  }
  @ReactMethod fun openUsagePermission(promise: Promise) {
    try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); promise.resolve(null) }
    catch (e: Exception) { promise.reject("GUARD_USAGE", e) }
  }
  @ReactMethod fun updateSession(value: ReadableMap, promise: Promise) {
    try {
      FocusGuardStore.session(ctx, value.getString("id") ?: "", value.getString("status") ?: "idle", value.getString("phase") ?: "focus",
        value.getString("mode") == "countdown", value.getDouble("remainingMs").toLong())
      promise.resolve(config())
    } catch (e: Exception) { promise.reject("GUARD_SESSION", e) }
  }
}
