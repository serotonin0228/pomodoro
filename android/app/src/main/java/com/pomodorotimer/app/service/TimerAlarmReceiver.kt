package com.pomodorotimer.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.elapsed

/**
 * Fires when the countdown reaches zero while the app is not ticking.
 *
 * It posts the completion notice and releases the foreground service; the persisted timer is left
 * untouched on purpose, so opening the app applies the normal completion transition exactly once.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = WorkspaceStore(context)
        val timer = store.load(null)?.state?.timer ?: return
        if (isForeignActive(timer, store.deviceId()) || timer.status != TimerStatus.RUNNING || timer.mode != Mode.COUNTDOWN) return
        if (elapsed(timer) < timer.duration) {
            Reminders.sync(context, timer)
            return
        }
        TimerCompletion.complete(context)
    }
}
