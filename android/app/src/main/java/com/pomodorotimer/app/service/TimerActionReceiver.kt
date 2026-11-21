package com.pomodorotimer.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pomodorotimer.app.data.PersistedWorkspace
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.data.MusicCoordinator
import com.pomodorotimer.app.focus.FocusGuardStore
import com.pomodorotimer.app.domain.remaining
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.finishState
import com.pomodorotimer.app.domain.newTimer
import com.pomodorotimer.app.domain.pauseState

/**
 * Notification actions, handled without waking the UI.
 *
 * The receiver applies the same pure domain transitions as the screen and writes them back to the
 * persisted workspace, then asks the service to refresh. The activity listens for
 * [ACTION_STATE_CHANGED] and reloads, so the two paths cannot diverge.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = WorkspaceStore(context)
        val workspace = store.loadActive() ?: return
        val current = workspace.state
        if (intent.getStringExtra(EXTRA_TIMER_ID) != current.timer.id ||
            current.timer.status !in setOf(TimerStatus.RUNNING, TimerStatus.PAUSED)) return
        if (isForeignActive(current.timer, store.deviceId())) return
        val now = Clock.now()

        when (intent.action) {
            ACTION_PAUSE -> {
                if (current.timer.status == TimerStatus.RUNNING) {
                    persist(context, store, pauseState(current, now))
                    TimerService.start(context)
                }
            }

            ACTION_RESUME -> {
                if (current.timer.status == TimerStatus.PAUSED) {
                    persist(
                        context,
                        store,
                        current.copy(
                            timer = current.timer.copy(status = TimerStatus.RUNNING, startedAt = now),
                            updatedAt = now,
                        ),
                    )
                    TimerService.start(context)
                }
            }

            ACTION_STOP -> {
                val finished = finishState(current, now, completed = false)
                persist(
                    context,
                    store,
                    finished.copy(
                        timer = newTimer(
                            device = current.timer.device,
                            settings = finished.settings,
                            mode = if (finished.timer.mode == Mode.COUNTDOWN) Mode.COUNTDOWN else Mode.STOPWATCH,
                            phase = Phase.FOCUS,
                            round = finished.round,
                            now = now,
                        ),
                        segments = emptyList(),
                    ),
                )
                TimerService.stop(context)
            }
        }
    }

    private fun persist(context: Context, store: WorkspaceStore, state: com.pomodorotimer.app.domain.AppState) {
        val account = store.activeAccount()
        val revision = store.load(account)?.revision ?: 0L
        store.save(account, PersistedWorkspace(state, pending = true, revision = revision))
        val timer = state.timer
        FocusGuardStore.session(context, timer.id, timer.status.name.lowercase(), timer.phase.name.lowercase(),
            timer.mode == Mode.COUNTDOWN, remaining(timer, Clock.now()) * 1000)
        runCatching { MusicCoordinator.sync(context, state) }
        runCatching { com.pomodorotimer.app.widget.TimerWidget.refresh(context) }
        context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val ACTION_PAUSE = "com.pomodorotimer.app.action.PAUSE"
        const val ACTION_RESUME = "com.pomodorotimer.app.action.RESUME"
        const val ACTION_STOP = "com.pomodorotimer.app.action.STOP"
        const val ACTION_STATE_CHANGED = "com.pomodorotimer.app.action.STATE_CHANGED"
    }
}
