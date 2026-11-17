package com.pomodorotimer.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerModeSwitchTest {
    @Test fun switchingRunningStopwatchPreservesElapsedWithoutCreatingARecord() {
        val initial = initialState("phone", 1_000)
        val running = initial.copy(timer = newTimer("phone", mode = Mode.STOPWATCH, now = 1_000).copy(status = TimerStatus.RUNNING, startedAt = 1_000))
        val suspended = modeDraft(running, 16_000)!!
        val countdown = switchModeState(suspended, null, Mode.COUNTDOWN, "phone", 16_000)
        val restored = switchModeState(countdown, suspended, Mode.STOPWATCH, "phone", 20_000)
        assertEquals(TimerStatus.PAUSED, restored.timer.status)
        assertEquals(15L, restored.timer.elapsed)
        assertEquals(listOf(Segment(1_000, 16_000)), restored.segments)
        assertEquals(emptyList<FocusRecord>(), restored.records)
        val finished = finishState(restored, 21_000)
        assertEquals(15L, finished.records.single().seconds)
    }

    @Test fun idleTimerIsNotSavedAsADraft() {
        assertNull(modeDraft(initialState("phone", 1_000), 2_000))
    }
}
