package com.pomodorotimer.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pomodorotimer.app.MainActivity
import com.pomodorotimer.app.R
import com.pomodorotimer.app.domain.Language
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerSession
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.clock
import com.pomodorotimer.app.domain.countdownDeadline
import com.pomodorotimer.app.domain.displaySeconds
import com.pomodorotimer.app.service.TimerActionReceiver

/**
 * Builds the ongoing countdown notification and the completion notification.
 *
 * The countdown is rendered by the system chronometer against the absolute end timestamp, so the
 * notification stays correct even while the app process is frozen.
 */
object TimerNotifications {

    const val ONGOING_ID = 1001
    const val FINISHED_ID = 1002

    fun ongoing(context: Context, timer: TimerSession, now: Long, language: Language): Notification {
        fun label(zh: String, en: String) = if (language == Language.EN) en else zh

        val running = timer.status == TimerStatus.RUNNING
        val title = when {
            timer.phase == Phase.BREAK && running -> label("正在休息", "On a break")
            timer.phase == Phase.BREAK -> label("休息已暂停", "Break paused")
            running -> label("正在专注", "Focusing")
            else -> label("专注已暂停", "Focus paused")
        }
        val value = clock(displaySeconds(timer, now))
        val builder = NotificationCompat.Builder(context, NotificationChannels.ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(value)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setShowWhen(false)

        val chronometer = notificationChronometerTime(timer, now, System.currentTimeMillis())
        if (chronometer != null) {
            builder.setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(timer.mode == Mode.COUNTDOWN)
                .setWhen(chronometer)
        } else {
            builder.setShortCriticalText(value)
        }

        if (running) {
            builder.addAction(action(context, TimerActionReceiver.ACTION_PAUSE, label("暂停", "Pause"), 1, timer.id))
        } else {
            builder.addAction(action(context, TimerActionReceiver.ACTION_RESUME, label("继续", "Resume"), 2, timer.id))
        }
        builder.addAction(action(context, TimerActionReceiver.ACTION_STOP, label("结束", "End"), 3, timer.id))
        return builder.build()
    }

    fun finished(context: Context, phase: Phase, sound: Boolean = true): Notification {
        NotificationChannels.ensure(context)
        val title = if (phase == Phase.FOCUS) "专注完成" else "休息结束"
        return Notification.Builder(context, if (sound) NotificationChannels.FINISHED else NotificationChannels.FINISHED_SILENT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText("打开番茄钟查看本次记录")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, action: String, label: String, code: Int, timerId: String): NotificationCompat.Action {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
            .setData(android.net.Uri.fromParts("pomodoro-timer", timerId, null))
            .putExtra(TimerActionReceiver.EXTRA_TIMER_ID, timerId)
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_timer, label, pending).build()
    }
}

/** Converts a timer's corrected timestamp to the wall-clock base expected by Android System UI. */
internal fun notificationChronometerTime(timer: TimerSession, now: Long, wallNow: Long): Long? {
    if (timer.status != TimerStatus.RUNNING) return null
    val start = timer.startedAt ?: return null
    val anchor = if (timer.mode == Mode.COUNTDOWN) {
        countdownDeadline(timer, 0L)?.takeIf { it > now } ?: return null
    } else {
        (start - timer.elapsed * 1000L).coerceAtMost(now)
    }
    return wallNow + (anchor - now)
}
