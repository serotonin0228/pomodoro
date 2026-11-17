package com.pomodorotimer.app.notifications

/** Media controls should be exposed only when both app and music-channel notifications are allowed. */
object MusicNotificationPolicy {
    fun showMediaControls(appNotificationsEnabled: Boolean, musicChannelEnabled: Boolean): Boolean =
        appNotificationsEnabled && musicChannelEnabled
}
