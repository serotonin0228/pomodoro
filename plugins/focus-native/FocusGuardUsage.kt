package com.pomodoro.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object FocusGuardUsage {
  fun allowed(context: Context): Boolean = try {
    context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
      Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
  } catch (_: Exception) { false }
  data class Foreground(val packageName: String, val at: Long)
  fun latest(context: Context, after: Long, now: Long): Foreground? {
    if (!allowed(context)) return null
    val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(maxOf(after - 1000, now - 15000), now)
    val event = UsageEvents.Event()
    var latest: Foreground? = null
    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      if (event.timeStamp <= after) continue
      if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = Foreground(event.packageName ?: "", event.timeStamp)
      else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED && latest?.packageName == event.packageName) latest = null
    }
    return latest
  }
}
