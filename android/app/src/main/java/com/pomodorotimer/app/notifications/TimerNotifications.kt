package com.pomodorotimer.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.pomodorotimer.app.MainActivity
import com.pomodorotimer.app.R
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerSession
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.clock
import com.pomodorotimer.app.domain.elapsed
import com.pomodorotimer.app.domain.remaining
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

    fun ongoing(context: Context, timer: TimerSession, now: Long): Notification {
        NotificationChannels.ensure(context)
        val phase = if (timer.phase == Phase.FOCUS) "专注" else "休息"
        val running = timer.status == TimerStatus.RUNNING

        val builder = Notification.Builder(context, NotificationChannels.ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(phase)
            .setContentIntent(openApp(context))
            .setOngoing(running)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (running) {
            builder.setShowWhen(true)
            if (timer.mode == Mode.COUNTDOWN) {
                builder.setUsesChronometer(true)
                builder.setChronometerCountDown(true)
                builder.setWhen(now + remaining(timer, now) * 1000)
                builder.setContentText("剩余 ${clock(remaining(timer, now))}")
            } else {
                builder.setUsesChronometer(true)
                builder.setWhen(now - elapsed(timer, now) * 1000)
                builder.setContentText("已专注 ${clock(elapsed(timer, now))}")
            }
            builder.addAction(
                action(context, TimerActionReceiver.ACTION_PAUSE, "暂停", 1),
            )
        } else {
            builder.setContentText("已暂停 · ${clock(displayNow(timer, now))}")
            builder.addAction(
                action(context, TimerActionReceiver.ACTION_RESUME, "继续", 2),
            )
        }
        builder.addAction(action(context, TimerActionReceiver.ACTION_STOP, "结束", 3))
        return builder.build()
    }

    fun finished(context: Context, phase: Phase): Notification {
        NotificationChannels.ensure(context)
        val title = if (phase == Phase.FOCUS) "专注完成" else "休息结束"
        return Notification.Builder(context, NotificationChannels.FINISHED)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText("打开番茄钟查看本次记录")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
    }

    private fun displayNow(timer: TimerSession, now: Long): Long =
        if (timer.mode == Mode.COUNTDOWN) remaining(timer, now) else elapsed(timer, now)

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

    private fun action(context: Context, action: String, label: String, code: Int): Notification.Action {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_timer)
        return Notification.Action.Builder(icon, label, pending).build()
    }

    private fun labelFor(action: String): String = when (action) {
        TimerActionReceiver.ACTION_PAUSE -> "暂停"
        TimerActionReceiver.ACTION_RESUME -> "继续"
        else -> "结束"
    }
}
