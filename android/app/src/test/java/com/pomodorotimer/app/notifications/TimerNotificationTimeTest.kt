package com.pomodorotimer.app.notifications

import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerSession
import com.pomodorotimer.app.domain.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerNotificationTimeTest {
    private val timer = TimerSession(
        id = "timer",
        mode = Mode.COUNTDOWN,
        phase = Phase.FOCUS,
        status = TimerStatus.RUNNING,
        duration = 60L,
        elapsed = 10L,
        startedAt = 10_000L,
        createdAt = 0L,
        device = "device",
    )

    @Test
    fun countdownKeepsTheSameDeadlineOnEveryRefresh() {
        assertEquals(62_000L, notificationChronometerTime(timer, 13_250L, 15_250L))
        assertEquals(62_000L, notificationChronometerTime(timer, 14_900L, 16_900L))
    }

    @Test
    fun stopwatchIncludesTimeAccumulatedBeforeResume() {
        val stopwatch = timer.copy(mode = Mode.STOPWATCH, elapsed = 8L)
        assertEquals(4_000L, notificationChronometerTime(stopwatch, 13_250L, 15_250L))
        assertEquals(4_000L, notificationChronometerTime(stopwatch, 14_900L, 16_900L))
    }

    @Test
    fun pausedOrExpiredTimersHaveNoRunningChronometer() {
        assertNull(notificationChronometerTime(timer.copy(status = TimerStatus.PAUSED), 14_900L, 16_900L))
        assertNull(notificationChronometerTime(timer, 60_000L, 62_000L))
    }
}
