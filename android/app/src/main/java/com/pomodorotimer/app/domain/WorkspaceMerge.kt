package com.pomodorotimer.app.domain

/** Preserve both histories; keep the most recently changed timer/settings as a consistent unit. */
fun mergeWorkspace(local: AppState, remote: AppState, allowPristine: Boolean = true): AppState {
    val pristine = allowPristine && local.records.isEmpty() && local.settings == Settings.Default &&
        local.timer.status == TimerStatus.IDLE && local.timer.mode == Mode.COUNTDOWN && local.round == 0 && local.segments.isEmpty()
    val base = if (pristine || remote.updatedAt > local.updatedAt) remote else local
    return base.copy(records = mergeRecords(local.records, remote.records))
}

/** Local edits made since the last acknowledged revision win, while both histories survive. */
fun mergePendingWorkspace(local: AppState, remote: AppState): AppState =
    local.copy(records = mergeRecords(local.records, remote.records))

/** An active session belongs to exactly one device. Older sessions without an owner stay usable. */
fun isForeignActive(timer: TimerSession, device: String): Boolean =
    (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED) &&
        timer.device.isNotBlank() && timer.device != device

/** Adopt the authoritative timer while retaining independently created history and local settings. */
fun mergeRemoteTimer(local: AppState, remote: AppState, keepLocalSettings: Boolean): AppState {
    val remoteTimer = remote.timer
    val remoteStillActive = remoteTimer.status == TimerStatus.RUNNING || remoteTimer.status == TimerStatus.PAUSED
    // A former owner may finish offline before learning that this session was claimed.
    val localRecords = if (remoteStillActive && remote.records.none { it.id == remoteTimer.id }) {
        local.records.filterNot { it.id == remoteTimer.id }
    } else local.records
    return remote.copy(
        records = mergeRecords(localRecords, remote.records),
        settings = if (keepLocalSettings) local.settings else remote.settings,
        updatedAt = if (keepLocalSettings) maxOf(local.updatedAt, remote.updatedAt) else remote.updatedAt,
    )
}

fun claimTimer(state: AppState, device: String, now: Long): AppState =
    state.copy(timer = state.timer.copy(device = device), updatedAt = maxOf(now, state.updatedAt + 1))

/** Move a locally started segment to a corrected clock without changing its elapsed time. */
fun rebaseRunningTimer(state: AppState, timerId: String?, oldOffset: Long, newOffset: Long): AppState {
    val timer = state.timer
    val start = timer.startedAt
    if (timer.id != timerId || timer.status != TimerStatus.RUNNING || start == null || oldOffset == newOffset) return state
    val delta = newOffset - oldOffset
    return state.copy(
        timer = timer.copy(startedAt = start + delta, createdAt = if (timer.createdAt == start) timer.createdAt + delta else timer.createdAt),
        updatedAt = state.updatedAt + delta,
    )
}
