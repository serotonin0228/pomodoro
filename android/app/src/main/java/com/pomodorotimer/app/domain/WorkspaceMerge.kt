package com.pomodorotimer.app.domain

/** Preserve both histories; keep the most recently changed timer/settings as a consistent unit. */
fun mergeWorkspace(local: AppState, remote: AppState): AppState {
    val pristine = local.records.isEmpty() && local.settings == Settings.Default &&
        local.timer.status == TimerStatus.IDLE && local.timer.mode == Mode.COUNTDOWN && local.round == 0 && local.segments.isEmpty()
    val base = if (pristine || remote.updatedAt > local.updatedAt) remote else local
    return base.copy(records = mergeRecords(local.records, remote.records))
}
