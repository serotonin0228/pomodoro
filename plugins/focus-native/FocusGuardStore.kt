package com.pomodoro.focus

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

object FocusGuardStore {
  fun prefs(context: Context) = context.getSharedPreferences("focus-guard", Context.MODE_PRIVATE)
  fun boot(context: Context): Int = try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) } catch (_: Exception) { -1 }
  fun session(context: Context, id: String, status: String, phase: String, countdown: Boolean, remainingMs: Long) {
    val uptime = SystemClock.elapsedRealtime()
    prefs(context).edit().putString("timerId", id).putString("status", status).putString("phase", phase)
      .putBoolean("countdown", countdown).putInt("boot", boot(context))
      .putLong("sessionUptime", uptime).putLong("bootEpoch", System.currentTimeMillis() - uptime)
      .putLong("deadline", uptime + remainingMs.coerceAtLeast(0)).apply()
  }
  fun isActive(context: Context): Boolean {
    val p = prefs(context)
    if (!p.getBoolean("enabled", false) || p.getString("status", "idle") != "running" || p.getString("phase", "focus") != "focus") return false
    val uptime = SystemClock.elapsedRealtime()
    val sameBoot = FocusGuardPolicy.sameBoot(boot(context), p.getInt("boot", -1), System.currentTimeMillis() - uptime,
      p.getLong("bootEpoch", 0), uptime, p.getLong("sessionUptime", Long.MAX_VALUE))
    return FocusGuardPolicy.isActive(p.getBoolean("enabled", false), p.getString("status", "idle") ?: "idle",
      p.getString("phase", "focus") ?: "focus", sameBoot,
      p.getBoolean("countdown", true), p.getLong("deadline", 0), SystemClock.elapsedRealtime())
  }
  // A notification's pause/resume action must work even while JavaScript is suspended.
  fun notificationAction(context: Context, id: String, status: String, phase: String, countdown: Boolean, remainingMs: Long) {
    if (prefs(context).getString("timerId", "") == id) session(context, id, status, phase, countdown, remainingMs)
  }
}
