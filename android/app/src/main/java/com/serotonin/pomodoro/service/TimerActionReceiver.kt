package com.serotonin.pomodoro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serotonin.pomodoro.data.PersistedWorkspace
import com.serotonin.pomodoro.data.WorkspaceStore
import com.serotonin.pomodoro.domain.Clock
import com.serotonin.pomodoro.domain.Mode
import com.serotonin.pomodoro.domain.Phase
import com.serotonin.pomodoro.domain.TimerStatus
import com.serotonin.pomodoro.domain.finishState
import com.serotonin.pomodoro.domain.newTimer
import com.serotonin.pomodoro.domain.pauseState

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
        val current = store.load(null)?.state ?: return
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

    private fun persist(context: Context, store: WorkspaceStore, state: com.serotonin.pomodoro.domain.AppState) {
        store.save(null, PersistedWorkspace(state, pending = true, revision = 0L))
        context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_PAUSE = "com.serotonin.pomodoro.action.PAUSE"
        const val ACTION_RESUME = "com.serotonin.pomodoro.action.RESUME"
        const val ACTION_STOP = "com.serotonin.pomodoro.action.STOP"
        const val ACTION_STATE_CHANGED = "com.serotonin.pomodoro.action.STATE_CHANGED"
    }
}
