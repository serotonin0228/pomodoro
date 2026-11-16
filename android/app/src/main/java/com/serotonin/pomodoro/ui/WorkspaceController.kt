package com.serotonin.pomodoro.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serotonin.pomodoro.data.PersistedWorkspace
import com.serotonin.pomodoro.data.WorkspaceStore
import com.serotonin.pomodoro.domain.AppState
import com.serotonin.pomodoro.domain.Clock
import com.serotonin.pomodoro.domain.Mode
import com.serotonin.pomodoro.domain.Phase
import com.serotonin.pomodoro.domain.Settings
import com.serotonin.pomodoro.domain.TimerStatus
import com.serotonin.pomodoro.domain.elapsed
import com.serotonin.pomodoro.domain.finishState
import com.serotonin.pomodoro.domain.initialState
import com.serotonin.pomodoro.domain.mergeRecords
import com.serotonin.pomodoro.domain.newTimer
import com.serotonin.pomodoro.domain.pauseState
import com.serotonin.pomodoro.domain.remaining
import com.serotonin.pomodoro.data.MusicCoordinator
import com.serotonin.pomodoro.focus.FocusGuardStore
import com.serotonin.pomodoro.service.Reminders
import com.serotonin.pomodoro.service.TimerService

enum class WorkspaceTab { TIMER, STATS, SETTINGS }

/**
 * Owns the workspace: loads it once, persists every mutation, and translates the three timer
 * states (idle / running / paused) into the same transitions the reference app performs.
 *
 * Nothing counts seconds: [tick] only compares absolute timestamps, so a suspended process, a
 * paused timer and a finished countdown all resolve to the same result.
 */
class WorkspaceController(context: Context) {

    private val appContext = context.applicationContext
    private val store = WorkspaceStore(appContext)
    private val account: String? = null

    val device: String = store.deviceId()

    var state: AppState by mutableStateOf(store.load(account)?.state ?: initialState(device))
        private set

    var tab: WorkspaceTab by mutableStateOf(WorkspaceTab.TIMER)

    /** Transient one-shot message shown by the shell. */
    var toast: String? by mutableStateOf(null)

    private var advanceAt: Long? = null

    val isRunning: Boolean get() = state.timer.status == TimerStatus.RUNNING
    val isPaused: Boolean get() = state.timer.status == TimerStatus.PAUSED
    val isCompleted: Boolean get() = state.timer.status == TimerStatus.COMPLETED
    val isActive: Boolean get() = state.timer.status == TimerStatus.RUNNING || state.timer.status == TimerStatus.PAUSED

    // ------------------------------------------------------------ transitions

    /** The big button: start, pause, resume, or continue to the next phase after completion. */
    fun primary() {
        val now = Clock.now()
        when (state.timer.status) {
            TimerStatus.COMPLETED -> advance(
                if (state.timer.phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS,
                start = true,
            )

            TimerStatus.RUNNING -> commit(pauseState(state, now))

            else -> commit(
                state.copy(
                    timer = state.timer.copy(
                        status = TimerStatus.RUNNING,
                        startedAt = now,
                        createdAt = if (state.timer.status == TimerStatus.IDLE) now else state.timer.createdAt,
                        device = device,
                    ),
                    updatedAt = now,
                ),
            )
        }
    }

    /** Ends the session and keeps the focus time that was actually observed. */
    fun endAndSave() {
        val now = Clock.now()
        val finished = finishState(state, now, completed = false)
        commit(
            finished.copy(
                timer = newTimer(device, finished.settings, state.timer.mode, Phase.FOCUS, finished.round, now),
                segments = emptyList(),
            ),
        )
    }

    /** Discards a session that just started, leaving no record behind. */
    fun endAndDiscard() {
        val now = Clock.now()
        commit(
            state.copy(
                timer = newTimer(device, state.settings, state.timer.mode, Phase.FOCUS, state.round, now),
                segments = emptyList(),
                updatedAt = now,
            ),
        )
    }

    fun changeMode(mode: Mode) {
        if (mode == state.timer.mode) return
        val now = Clock.now()
        val finished = if (isActive) finishState(state, now, completed = false) else state
        commit(
            finished.copy(
                timer = newTimer(device, finished.settings, mode, Phase.FOCUS, finished.round, now),
                segments = emptyList(),
                updatedAt = now,
            ),
        )
    }

    fun advance(phase: Phase, start: Boolean) {
        val now = Clock.now()
        val created = newTimer(
            device = device,
            settings = state.settings,
            mode = if (phase == Phase.BREAK) Mode.COUNTDOWN else state.timer.mode,
            phase = phase,
            round = state.round,
            now = now,
        )
        commit(
            state.copy(
                timer = if (start) created.copy(status = TimerStatus.RUNNING, startedAt = now) else created,
                segments = emptyList(),
                updatedAt = now,
            ),
        )
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val now = Clock.now()
        val next = transform(state.settings)
        val reshapesTimer = next.focusMinutes != state.settings.focusMinutes ||
            next.breakMinutes != state.settings.breakMinutes ||
            next.longBreakMinutes != state.settings.longBreakMinutes ||
            next.rounds != state.settings.rounds
        val timer = if (state.timer.status == TimerStatus.IDLE && reshapesTimer) {
            newTimer(device, next, state.timer.mode, state.timer.phase, state.round, now)
        } else {
            state.timer
        }
        commit(state.copy(settings = next, timer = timer, updatedAt = now))
    }

    fun deleteRecord(id: String) {
        val now = Clock.now()
        commit(
            state.copy(
                records = state.records.map { if (it.id == id) it.copy(deleted = true) else it },
                updatedAt = now,
            ),
        )
    }

    // ------------------------------------------------------------ ticking

    /** Re-arms the service and alarm for a timer restored from disk. */
    fun ensureSystemState() = syncSystemState(state)

    /** Merges a cloud copy into the local workspace, keeping both sides history. */
    fun applyRemote(remote: AppState) {
        val merged = remote.copy(
            records = mergeRecords(state.records, remote.records),
            settings = if (state.updatedAt >= remote.updatedAt) state.settings else remote.settings,
        )
        commit(merged)
    }

    /** Reloads the workspace after a notification action changed it behind the screen. */
    fun refresh() {
        val loaded = store.load(account)?.state ?: return
        if (loaded.updatedAt != state.updatedAt) state = loaded
    }

    /** Reloads the workspace after a notification action changed it behind the screen. */
    fun refresh() {
        val loaded = store.load(account)?.state ?: return
        if (loaded.updatedAt != state.updatedAt) state = loaded
    }

    /** Re-arms the service and the alarm for a timer that was restored from disk. */
    fun ensureSystemState() = syncSystemState(state)

    fun tick(now: Long = Clock.now()) {
        val timer = state.timer

        if (timer.status == TimerStatus.RUNNING &&
            timer.mode == Mode.COUNTDOWN &&
            elapsed(timer, now) >= timer.duration
        ) {
            val finished = finishState(state, now, completed = true)
            val autoNext = if (timer.phase == Phase.FOCUS) state.settings.autoBreak else state.settings.autoFocus
            advanceAt = if (autoNext) now + AUTO_ADVANCE_DELAY_MS else null
            commit(finished)
            toast = if (timer.phase == Phase.FOCUS) "专注完成" else "休息结束"
            return
        }

        val due = advanceAt
        if (due != null && now >= due) {
            advanceAt = null
            advance(if (state.timer.phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS, start = true)
        }
    }

    // ------------------------------------------------------------ persistence

    private fun commit(next: AppState) {
        state = next
        store.save(account, PersistedWorkspace(next, pending = true, revision = 0L))
        syncSystemState(next)
    }

    /** Keeps the foreground service and the exact alarm in step with the timer. */
    private fun syncSystemState(next: AppState) {
        val timer = next.timer
        // The accessibility service reads this snapshot instead of querying JavaScript.
        runCatching {
            FocusGuardStore.session(
                appContext,
                timer.id,
                when (timer.status) {
                    TimerStatus.RUNNING -> "running"
                    TimerStatus.PAUSED -> "paused"
                    TimerStatus.COMPLETED -> "completed"
                    else -> "idle"
                },
                if (timer.phase == com.serotonin.pomodoro.domain.Phase.FOCUS) "focus" else "break",
                timer.mode == com.serotonin.pomodoro.domain.Mode.COUNTDOWN,
                remaining(timer, Clock.now()) * 1000,
            )
        }
        runCatching { MusicCoordinator.sync(appContext, next) }
        if (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED) {
            runCatching { TimerService.start(appContext) }
        } else {
            runCatching { TimerService.stop(appContext) }
            Reminders.cancel(appContext)
        }
    }

    private companion object {
        const val AUTO_ADVANCE_DELAY_MS = 1200L
    }
}
