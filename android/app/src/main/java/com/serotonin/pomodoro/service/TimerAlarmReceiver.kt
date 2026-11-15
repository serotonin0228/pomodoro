package com.serotonin.pomodoro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serotonin.pomodoro.data.WorkspaceStore
import com.serotonin.pomodoro.domain.Phase

/**
 * Fires when the countdown reaches zero while the app is not ticking.
 *
 * It posts the completion notice and releases the foreground service; the persisted timer is left
 * untouched on purpose, so opening the app applies the normal completion transition exactly once.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timer = WorkspaceStore(context).load(null)?.state?.timer
        TimerService.notifyFinished(context, timer?.phase ?: Phase.FOCUS)
        TimerService.stop(context)
    }
}
