package com.pomodorotimer.app.domain

/** Suspend a running session without creating a history record when changing modes. */
fun modeDraft(state: AppState, now: Long): AppState? = when (state.timer.status) {
    TimerStatus.RUNNING -> pauseState(state, now)
    TimerStatus.PAUSED -> state
    else -> null
}

/** Restore only the timer and its segments; current settings and history remain authoritative. */
fun switchModeState(current: AppState, draft: AppState?, mode: Mode, device: String, now: Long): AppState {
    val usable = draft?.takeIf { it.timer.mode == mode && it.timer.status == TimerStatus.PAUSED }
    return current.copy(
        timer = usable?.timer ?: newTimer(device, current.settings, mode, Phase.FOCUS, current.round, now),
        segments = usable?.segments ?: emptyList(),
        updatedAt = now,
    )
}
