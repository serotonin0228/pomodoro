package com.pomodorotimer.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val ONGOING = "timer_ongoing"
    const val FINISHED = "timer_finished"
    const val MUSIC = "music_playback"

    fun musicNotificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getNotificationChannel(MUSIC)?.importance != NotificationManager.IMPORTANCE_NONE
        } else true
        return MusicNotificationPolicy.showMediaControls(manager.areNotificationsEnabled(), channelEnabled)
    }

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    ONGOING,
                    "计时进行中",
                    // Silent: the countdown is always visible in the shade, it must not buzz.
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
        if (manager.getNotificationChannel(MUSIC) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    MUSIC,
                    "音乐播放",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                },
            )
        }
        if (manager.getNotificationChannel(FINISHED) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FINISHED,
                    "计时结束",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }
}
