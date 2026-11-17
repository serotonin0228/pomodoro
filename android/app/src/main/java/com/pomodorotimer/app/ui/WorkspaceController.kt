package com.pomodorotimer.app.ui

import android.content.Context
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.data.CloudClient
import com.pomodorotimer.app.data.StateCodec
import com.pomodorotimer.app.domain.modeDraft
import com.pomodorotimer.app.domain.switchModeState
import kotlinx.coroutines.sync.Mutex
import com.pomodorotimer.app.domain.mergeWorkspace
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pomodorotimer.app.data.PersistedWorkspace
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.AppState
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.Settings
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.elapsed
import com.pomodorotimer.app.domain.finishState
import com.pomodorotimer.app.domain.initialState
import com.pomodorotimer.app.domain.mergeRecords
import com.pomodorotimer.app.domain.newTimer
import com.pomodorotimer.app.domain.pauseState
import com.pomodorotimer.app.domain.remaining
import com.pomodorotimer.app.data.MusicCoordinator
import com.pomodorotimer.app.focus.FocusGuardStore
import com.pomodorotimer.app.service.Reminders
import com.pomodorotimer.app.service.TimerService

enum class WorkspaceTab { TIMER, STATS, SETTINGS }
enum class CloudSyncStatus { OFFLINE, PENDING, SYNCING, SYNCED, ERROR }

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
    var cloudSyncStatus by mutableStateOf(
        if (appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE).contains("session")) CloudSyncStatus.PENDING else CloudSyncStatus.OFFLINE
    )
        private set
    val cloudSyncError: Boolean get() = cloudSyncStatus == CloudSyncStatus.ERROR
    private val syncMutex = Mutex()
    private val drafts = appContext.getSharedPreferences("timer_mode_drafts", Context.MODE_PRIVATE)

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
        val draft = modeDraft(state, now)
        val departing = draft ?: state
        val destinationKey = mode.name
        val saved = drafts.getString(destinationKey, null)?.let { StateCodec.decode(it) }
        val editor = drafts.edit().remove(destinationKey)
        if (draft != null) editor.putString(state.timer.mode.name, StateCodec.encode(draft))
        else editor.remove(state.timer.mode.name)
        editor.apply()
        commit(switchModeState(departing, saved, mode, device, now))
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

    /** Merge without replacing a more recent local timer or losing either device's history. */
    fun applyRemote(remote: AppState) {
        val merged = mergeWorkspace(state, remote)
        if (merged != state) commit(merged)
    }

    /** One automatic synchronization cycle. It never writes until it has read the current revision. */
    suspend fun syncCloud() {
        val prefs = appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        val original = prefs.getString("session", null) ?: run { cloudSyncStatus = CloudSyncStatus.OFFLINE; return }
        if (!syncMutex.tryLock()) return
        try {
        cloudSyncStatus = CloudSyncStatus.SYNCING
        var raw = original
        var session = runCatching {
            val o = JSONObject(raw)
            CloudClient.Session(o.getString("userId"), o.getString("access"), o.optString("refresh"))
        }.getOrNull() ?: run { cloudSyncStatus = CloudSyncStatus.ERROR; return }
        val client = CloudClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        if (!client.configured) { cloudSyncStatus = CloudSyncStatus.ERROR; return }
        try {
            var attempt = 0
            while (attempt++ < 3) {
                try {
                    val remote = client.fetch(session)
                    if (prefs.getString("session", null) != raw) return
                    val merged = if (remote == null) state else mergeWorkspace(state, remote.state)
                    var synchronizedState: AppState
                    if (remote == null || merged != remote.state) {
                        val saved = client.save(session, merged, remote?.revision ?: 0L, device)
                        if (prefs.getString("session", null) != raw) return
                        prefs.edit().putLong("revision", saved.revision).apply()
                        applyRemote(saved.state)
                        synchronizedState = saved.state
                    } else {
                        prefs.edit().putLong("revision", remote.revision).apply()
                        applyRemote(remote.state)
                        synchronizedState = remote.state
                    }
                    cloudSyncStatus = if (state == synchronizedState) CloudSyncStatus.SYNCED else CloudSyncStatus.PENDING
                    return
                } catch (_: CloudClient.ConflictException) {
                    // A second device wrote meanwhile; re-read and merge on the next attempt.
                } catch (expired: CloudClient.AuthExpiredException) {
                    if (session.refreshToken.isEmpty()) throw expired
                    val refreshed = client.refresh(session)
                    if (prefs.getString("session", null) != raw) return
                    raw = JSONObject().put("userId", refreshed.userId).put("access", refreshed.accessToken)
                        .put("refresh", refreshed.refreshToken).toString()
                    prefs.edit().putString("session", raw).apply()
                    session = refreshed
                }
            }
            cloudSyncStatus = CloudSyncStatus.ERROR
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            cloudSyncStatus = CloudSyncStatus.ERROR
        }
        } finally {
            syncMutex.unlock()
        }
    }

    /** Reloads the workspace after a notification action changed it behind the screen. */
    fun refresh() {
        val loaded = store.load(account)?.state ?: return
        if (loaded.updatedAt != state.updatedAt) state = loaded
    }

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
        if (appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE).contains("session")) cloudSyncStatus = CloudSyncStatus.PENDING
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
                if (timer.phase == com.pomodorotimer.app.domain.Phase.FOCUS) "focus" else "break",
                timer.mode == com.pomodorotimer.app.domain.Mode.COUNTDOWN,
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
