package com.pomodorotimer.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.notifications.NotificationChannels
import com.pomodorotimer.app.notifications.TimerNotifications

/**
 * Foreground service that keeps a running or paused timer alive and visible.
 *
 * It owns no timer state: it re-reads the persisted workspace, so the UI, the notification actions
 * and the service can never disagree about what is running.
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTimer()
            return START_NOT_STICKY
        }

        val store = WorkspaceStore(this)
        val timer = store.load(null)?.state?.timer
        if (timer == null || isForeignActive(timer, store.deviceId()) || (timer.status != TimerStatus.RUNNING && timer.status != TimerStatus.PAUSED)) {
            stopTimer()
            return START_NOT_STICKY
        }

        NotificationChannels.ensure(this)
        startForegroundCompat(TimerNotifications.ongoing(this, timer, Clock.now()))
        Reminders.sync(this, timer)
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                TimerNotifications.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(TimerNotifications.ONGOING_ID, notification)
        }
    }

    private fun stopTimer() {
        Reminders.cancel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.pomodorotimer.app.action.START_TIMER"
        const val ACTION_STOP = "com.pomodorotimer.app.action.STOP_TIMER"

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }

        /** Completion notice, used when the process was asleep and the alarm is what woke us. */
        fun notifyFinished(context: Context, phase: Phase) {
            NotificationChannels.ensure(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching {
                manager.notify(
                    TimerNotifications.FINISHED_ID,
                    TimerNotifications.finished(context, phase),
                )
            }
        }
    }
}
