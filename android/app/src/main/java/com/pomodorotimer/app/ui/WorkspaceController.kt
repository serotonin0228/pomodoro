package com.pomodorotimer.app.ui

import android.content.Context
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.data.CloudClient
import com.pomodorotimer.app.data.MusicDownloads
import com.pomodorotimer.app.data.StateCodec
import com.pomodorotimer.app.domain.modeDraft
import com.pomodorotimer.app.domain.switchModeState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.mergeRemoteTimer
import com.pomodorotimer.app.domain.claimTimer
import com.pomodorotimer.app.domain.rebaseRunningTimer
import com.pomodorotimer.app.domain.mergeWorkspace
import com.pomodorotimer.app.domain.mergePendingWorkspace
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
import com.pomodorotimer.app.service.TimerCompletion

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
    var account: String? by mutableStateOf(store.activeAccount())
        private set

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
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private var localGeneration = 0L
    private var pendingLocal = store.load(account)?.pending == true
    private var workspaceRevision = store.load(account)?.revision ?: 0L
    private var lastClockCheck = 0L
    private var lastFullFetchAt: Long? = null
    private var locallyStartedSinceClockCheck: String? = null
    private val drafts = appContext.getSharedPreferences("timer_mode_drafts", Context.MODE_PRIVATE)


    val isRemoteActive: Boolean get() = isForeignActive(state.timer, device)
    fun requestSync() { syncRequests.trySend(Unit) }
    suspend fun awaitSync() {
        val interval = if (isActive || tab == WorkspaceTab.TIMER) 4_000L else 12_000L
        withTimeoutOrNull(interval) { syncRequests.receive() }
    }

    fun switchWorkspace() {
        val nextAccount = store.activeAccount()
        if (nextAccount == account) return
        MusicDownloads.cancelAll()
        runCatching { MusicCoordinator.stop(appContext) }
        account = nextAccount
        val snapshot = store.load(account)
        state = snapshot?.state ?: initialState(device)
        localGeneration++
        pendingLocal = snapshot?.pending == true
        workspaceRevision = snapshot?.revision ?: 0L
        lastFullFetchAt = null
        locallyStartedSinceClockCheck = null
        cloudSyncStatus = if (account == null) CloudSyncStatus.OFFLINE else CloudSyncStatus.PENDING
        syncSystemState(state)
        requestSync()
    }

    val isRunning: Boolean get() = state.timer.status == TimerStatus.RUNNING
    val isPaused: Boolean get() = state.timer.status == TimerStatus.PAUSED
    val isCompleted: Boolean get() = state.timer.status == TimerStatus.COMPLETED
    val isActive: Boolean get() = state.timer.status == TimerStatus.RUNNING || state.timer.status == TimerStatus.PAUSED

    // ------------------------------------------------------------ transitions

    /** The big button: start, pause, resume, or continue to the next phase after completion. */
    fun primary() {
        if (isRemoteActive) return
        val now = Clock.now()
        when (state.timer.status) {
            TimerStatus.COMPLETED -> advance(
                if (state.timer.phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS,
                start = true,
            )

            TimerStatus.RUNNING -> commit(pauseState(state, now))

            else -> {
                locallyStartedSinceClockCheck = state.timer.id
                commit(
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
    }

    /** Ends the session and keeps the focus time that was actually observed. */
    fun endAndSave() {
        if (isRemoteActive) return
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
        if (isRemoteActive) return
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
        if (isRemoteActive) return
        if (mode == state.timer.mode) return
        val now = Clock.now()
        val draft = modeDraft(state, now)
        val departing = draft ?: state
        val prefix = "${account ?: "guest"}:"
        val destinationKey = prefix + mode.name
        val saved = drafts.getString(destinationKey, null)?.let { StateCodec.decode(it) }
        val editor = drafts.edit().remove(destinationKey)
        if (draft != null) editor.putString(prefix + state.timer.mode.name, StateCodec.encode(draft))
        else editor.remove(prefix + state.timer.mode.name)
        editor.apply()
        commit(switchModeState(departing, saved, mode, device, now))
    }

    fun advance(phase: Phase, start: Boolean) {
        if (isRemoteActive) return
        val now = Clock.now()
        val created = newTimer(
            device = device,
            settings = state.settings,
            mode = if (phase == Phase.BREAK) Mode.COUNTDOWN else state.timer.mode,
            phase = phase,
            round = state.round,
            now = now,
        )
        if (start) locallyStartedSinceClockCheck = created.id
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

    fun restoreHistory(records: List<com.pomodorotimer.app.domain.FocusRecord>, expectedAccount: String?): Int {
        check(account == expectedAccount && store.activeAccount() == expectedAccount)
        val merged = com.pomodorotimer.app.domain.mergeRecords(state.records, records)
        val added = merged.count { !it.deleted } - state.records.count { !it.deleted }
        if (merged != state.records) commit(state.copy(records = merged, updatedAt = Clock.now()))
        return added
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

    /** Pull before every write; server revisions prevent one device overwriting another. */
    suspend fun syncCloud() {
        val prefs = appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        var raw = prefs.getString("session", null) ?: run { cloudSyncStatus = CloudSyncStatus.OFFLINE; return }
        if (!syncMutex.tryLock()) return
        try {
            if (cloudSyncStatus != CloudSyncStatus.SYNCED) cloudSyncStatus = CloudSyncStatus.SYNCING
            var session = parseSession(raw) ?: run { cloudSyncStatus = CloudSyncStatus.ERROR; return }
            if (session.userId != account) return
            val client = CloudClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
            if (!client.configured) { cloudSyncStatus = CloudSyncStatus.ERROR; return }
            repeat(3) {
                try {
                    if (android.os.SystemClock.elapsedRealtime() - lastClockCheck > 300_000L || lastClockCheck == 0L) {
                        val beforeWall = System.currentTimeMillis()
                        val beforeMono = android.os.SystemClock.elapsedRealtime()
                        val server = client.serverTime(session)
                        if (prefs.getString("session", null) != raw) return
                        val afterMono = android.os.SystemClock.elapsedRealtime()
                        val afterWall = System.currentTimeMillis()
                        val roundTrip = afterMono - beforeMono
                        // A slow or interrupted sample has too much uncertainty to calibrate a clock.
                        if (server != null && roundTrip in 0..2000L &&
                            kotlin.math.abs((afterWall - beforeWall) - roundTrip) < 100L) {
                            val wallOffset = server - (beforeWall + roundTrip / 2)
                            val offset = server - (Clock.uncorrectedNow() - roundTrip / 2)
                            val oldOffset = Clock.correction()
                            val rebased = if (isRemoteActive) state else rebaseRunningTimer(state, locallyStartedSinceClockCheck, oldOffset, offset)
                            Clock.setCorrection(offset)
                            locallyStartedSinceClockCheck = null
                            prefs.edit().putLong("clock_offset", wallOffset).apply()
                            lastClockCheck = afterMono
                            if (rebased != state) commit(rebased)
                        }
                    }
                    val fetchTime = android.os.SystemClock.elapsedRealtime()
                    val fullFetch = pendingLocal || workspaceRevision <= 0 ||
                        (lastFullFetchAt?.let { fetchTime - it >= 300_000L } ?: true)
                    val revisionFilter = if (fullFetch) null else workspaceRevision
                    val remote = client.fetch(session, afterRevision = revisionFilter)
                    if (prefs.getString("session", null) != raw) return
                    if (fullFetch) lastFullFetchAt = fetchTime
                    if (remote == null && revisionFilter != null) {
                        if (!pendingLocal) cloudSyncStatus = CloudSyncStatus.SYNCED
                        return
                    }
                    val local = state
                    val generation = localGeneration
                    val knownRevision = workspaceRevision
                    val remoteChanged = remote != null && remote.revision > knownRevision
                    val adoptRemote = remoteChanged && remote != null && (
                        !pendingLocal || isForeignActive(local.timer, device) ||
                        (isForeignActive(remote.state.timer, device) && local.timer.status == TimerStatus.IDLE) ||
                        (local.timer.id == remote.state.timer.id && local.timer.device != remote.state.timer.device)
                    )
                    val merged = when {
                        remote == null -> local
                        adoptRemote -> mergeRemoteTimer(local, remote.state, pendingLocal)
                        pendingLocal && !remoteChanged -> mergePendingWorkspace(local, remote.state)
                        else -> mergeWorkspace(local, remote.state, allowPristine = !pendingLocal)
                    }
                    val saved = if (remote == null || merged != remote.state)
                        client.save(session, merged, remote?.revision ?: 0L, device) else remote
                    if (prefs.getString("session", null) != raw) return
                    val result = when {
                        generation == localGeneration -> saved.state
                        adoptRemote && isForeignActive(saved.state.timer, device) ->
                            mergeRemoteTimer(state, saved.state, keepLocalSettings = true)
                        else -> mergePendingWorkspace(state, saved.state)
                    }
                    acceptSynced(result, saved.state, saved.revision)
                    return
                } catch (_: CloudClient.ConflictException) {
                    // Re-fetch and re-evaluate ownership before retrying.
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
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { cloudSyncStatus = CloudSyncStatus.ERROR
        } finally { syncMutex.unlock() }
    }

    private fun parseSession(raw: String): CloudClient.Session? = runCatching {
        val o = JSONObject(raw)
        CloudClient.Session(o.getString("userId"), o.getString("access"), o.optString("refresh"))
    }.getOrNull()

    /** Take ownership only after an atomic revision-checked server write succeeds. */
    suspend fun takeover(): Boolean = syncMutex.withLock {
        val targetId = state.timer.id
        val prefs = appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE)
        val raw = prefs.getString("session", null) ?: return@withLock false
        val session = parseSession(raw) ?: return@withLock false
        if (session.userId != account) return@withLock false
        val client = CloudClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        try {
            repeat(3) {
                try {
                    val remote = client.fetch(session) ?: return@withLock false
                    if (prefs.getString("session", null) != raw || remote.state.timer.id != targetId ||
                        !isForeignActive(remote.state.timer, device)) return@withLock false
                    val generation = localGeneration
                    val claimed = claimTimer(mergeRemoteTimer(state, remote.state, pendingLocal), device, Clock.now())
                    val saved = client.save(session, claimed, remote.revision, device)
                    if (prefs.getString("session", null) != raw) return@withLock false
                    val result = if (generation == localGeneration) saved.state
                        else mergeRemoteTimer(state, saved.state, true)
                    acceptSynced(result, saved.state, saved.revision)
                    return@withLock true
                } catch (_: CloudClient.ConflictException) { }
            }
            false
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { cloudSyncStatus = CloudSyncStatus.ERROR; false }
    }

    private fun acceptSynced(next: AppState, saved: AppState, revision: Long) {
        workspaceRevision = revision
        if (next != state) {
            state = next
            store.save(account, PersistedWorkspace(next, next != saved, revision))
            syncSystemState(next)
        } else store.save(account, PersistedWorkspace(next, next != saved, revision))
        pendingLocal = next != saved
        cloudSyncStatus = if (pendingLocal) CloudSyncStatus.PENDING else CloudSyncStatus.SYNCED
        if (pendingLocal) requestSync()
    }

    /** Reloads the workspace after a notification action changed it behind the screen. */
    fun refresh() {
        val loaded = store.load(account)?.state ?: return
        if (loaded != state) {
            state = loaded
            pendingLocal = true
            localGeneration++
            requestSync()
        }
    }

    fun tick(now: Long = Clock.now()) {
        val timer = state.timer
        if (isRemoteActive) return

        if (timer.status == TimerStatus.RUNNING && timer.mode == Mode.COUNTDOWN && elapsed(timer, now) >= timer.duration) {
            // Notification actions and the alarm can update disk while the activity is backgrounded.
            val persisted = store.load(account)?.state
            if (persisted != null && persisted != state) { refresh(); return }
            if (TimerCompletion.complete(appContext, now, timer.id)) {
                refresh()
                toast = if (timer.phase == Phase.FOCUS) "专注完成" else "休息结束"
            }
        }
    }

    // ------------------------------------------------------------ persistence

    private fun commit(next: AppState) {
        state = next
        pendingLocal = true
        localGeneration++
        requestSync()
        if (appContext.getSharedPreferences("cloud", Context.MODE_PRIVATE).contains("session")) cloudSyncStatus = CloudSyncStatus.PENDING
        store.save(account, PersistedWorkspace(next, pending = true, revision = workspaceRevision))
        syncSystemState(next)
    }

    /** Keeps the foreground service and the exact alarm in step with the timer. */
    private fun syncSystemState(next: AppState) {
        runCatching { com.pomodorotimer.app.widget.TimerWidget.refresh(appContext) }
        val timer = next.timer
        val foreign = isForeignActive(timer, device)
        // The accessibility service reads this snapshot instead of querying JavaScript.
        runCatching {
            FocusGuardStore.session(
                appContext,
                timer.id,
                when (if (foreign) TimerStatus.IDLE else timer.status) {
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
        runCatching { MusicCoordinator.sync(appContext, if (foreign) next.copy(timer = timer.copy(status = TimerStatus.IDLE)) else next) }
        if (!foreign && (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED)) {
            runCatching { TimerService.start(appContext) }
        } else {
            runCatching { TimerService.stop(appContext) }
            Reminders.cancel(appContext)
        }
    }

}
