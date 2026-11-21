package com.pomodorotimer.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.TimerSession
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.countdownDeadline

/**
 * Exact "time is up" alarm.
 *
 * The foreground service keeps the notification honest, but Android may still kill the process;
 * an exact alarm is what guarantees the completion notification appears on time. When exact alarms
 * are not permitted (Android 12+ user toggle), it degrades to an inexact idle-safe alarm rather than
 * silently dropping the reminder.
 */
object Reminders {

    private const val REQUEST_CODE = 2001

    fun sync(context: Context, timer: TimerSession) {
        cancel(context)
        if (timer.status != TimerStatus.RUNNING || timer.mode != Mode.COUNTDOWN) return
        val deadline = countdownDeadline(timer, 0L) ?: return
        val triggerAt = android.os.SystemClock.elapsedRealtime() + (deadline - Clock.now()).coerceAtLeast(0L)

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, timer.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context, timerId: String? = null): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
            .putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timerId)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
