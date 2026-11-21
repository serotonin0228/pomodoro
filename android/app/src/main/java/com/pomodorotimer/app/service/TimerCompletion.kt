package com.pomodorotimer.app.service

import android.content.Context
import android.content.Intent
import com.pomodorotimer.app.data.MusicCoordinator
import com.pomodorotimer.app.data.PersistedWorkspace
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.completeCountdown
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.remaining
import com.pomodorotimer.app.focus.FocusGuardStore

/** Shared completion path for the activity and the alarm, including when the UI is not running. */
object TimerCompletion {
    fun complete(context: Context, now: Long = Clock.now(), expectedTimerId: String): Boolean {
        val store = WorkspaceStore(context)
        val account = store.activeAccount()
        val workspace = store.load(account) ?: return false
        val current = workspace.state
        if (current.timer.id != expectedTimerId) return false
        if (isForeignActive(current.timer, store.deviceId())) return false
        val next = completeCountdown(current, store.deviceId(), now)
        if (next == current) return false
        store.save(account, PersistedWorkspace(next, pending = true, revision = workspace.revision))
        val timer = next.timer
        FocusGuardStore.session(context, timer.id, timer.status.name.lowercase(), timer.phase.name.lowercase(),
            timer.mode == Mode.COUNTDOWN, remaining(timer, now) * 1000)
        runCatching { MusicCoordinator.sync(context, next) }
        if (timer.status == TimerStatus.RUNNING) runCatching { TimerService.start(context) }
        else { TimerService.stop(context); Reminders.cancel(context) }
        TimerService.notifyFinished(context, current.timer.phase, current.settings)
        com.pomodorotimer.app.widget.refreshWorkspaceWidgets(context)
        context.sendBroadcast(Intent(TimerActionReceiver.ACTION_STATE_CHANGED).setPackage(context.packageName))
        return true
    }
}
