package com.pomodorotimer.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural contract ported from `tests/model.test.ts` of the React Native app.
 * Every case here guards a rule that the rewrite must not silently change.
 */
class ModelTest {

    @Test
    fun `timer follows wall clock time and excludes a pause, even after a long suspension`() {
        var s = initialState("device", now = 0)
        s = s.copy(timer = s.timer.copy(status = TimerStatus.RUNNING, startedAt = 1000))
        s = pauseState(s, 61000)
        assertEquals(60L, elapsed(s.timer, 600000))

        s = s.copy(timer = s.timer.copy(status = TimerStatus.RUNNING, startedAt = 600000))
        val done = finishState(s, 660000)
        assertEquals(120L, done.records[0].seconds)
        assertEquals(2, done.records[0].segments.size)
    }

    @Test
    fun `countdown recovery clips time at the original deadline and saves exactly once`() {
        var s = initialState("device", now = 0)
        s = s.copy(timer = s.timer.copy(duration = 60, status = TimerStatus.RUNNING, startedAt = 1000))
        val finished = finishState(s, 999000, completed = true)
        assertEquals(60L, finished.records[0].seconds)
        assertEquals(61000L, finished.records[0].end)
        assertEquals(1, finishState(finished, 1000000, completed = true).records.size)
    }

    @Test
    fun `rest never becomes focus data`() {
        var s = initialState("device", now = 0)
        s = s.copy(timer = s.timer.copy(phase = Phase.BREAK, status = TimerStatus.RUNNING, startedAt = 1000))
        assertEquals(0, finishState(s, 61000).records.size)
    }

    @Test
    fun `statistics split at local midnight and do not double count overlapping devices`() {
        val start = LocalDateTime.of(2026, 11, 6, 23, 50)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val a = FocusRecord(
            id = "a", mode = Mode.STOPWATCH, start = start, end = start + 1_200_000,
            seconds = 1200, segments = listOf(Segment(start, start + 1_200_000)),
        )
        val b = a.copy(
            id = "b", start = start + 300_000, end = start + 900_000,
            segments = listOf(Segment(start + 300_000, start + 900_000)),
        )
        assertEquals(mapOf("2026-11-06" to 600L, "2026-11-07" to 600L), dailyTotals(listOf(a, b)))
        assertTrue(dailyTotals(listOf(a.copy(deleted = true))).isEmpty())
    }

    @Test
    fun `deleted records cannot be resurrected by an older cloud copy`() {
        val r = FocusRecord(
            id = "a", mode = Mode.COUNTDOWN, start = 1000, end = 61000, seconds = 60,
            segments = listOf(Segment(1000, 61000)),
        )
        assertTrue(mergeRecords(listOf(r.copy(deleted = true)), listOf(r))[0].deleted)
    }

    @Test
    fun `long breaks respect the configured round interval`() {
        val s = initialState("device", now = 0)
        assertEquals(900L, newTimer("device", s.settings, phase = Phase.BREAK, round = 4).duration)
        assertEquals(300L, newTimer("device", s.settings, phase = Phase.BREAK, round = 3).duration)
        assertEquals("1:01:01", clock(3661))
    }

    @Test
    fun `merging divergent copies of one session preserves unique intervals without duplicates`() {
        val a = FocusRecord(
            id = "same", mode = Mode.STOPWATCH, start = 1000, end = 11000, seconds = 10,
            segments = listOf(Segment(1000, 11000)),
        )
        val b = a.copy(
            end = 26000, seconds = 15,
            segments = listOf(Segment(6000, 16000), Segment(21000, 26000)),
        )
        val merged = mergeRecords(listOf(a), listOf(b))[0]
        assertEquals(20L, merged.seconds)
        assertEquals(listOf(Segment(1000, 16000), Segment(21000, 26000)), merged.segments)
    }

    @Test
    fun `goal progress follows the selected dates, excludes future targets and deleted sessions`() {
        val state = initialState("device", now = 0)
        val d1 = LocalDate.of(2025, 1, 6)
        val d2 = LocalDate.of(2025, 1, 7)
        val d3 = LocalDate.of(2025, 1, 8)
        val totals = mapOf("2025-01-06" to 3600L, "2025-01-07" to 1800L, "2025-01-08" to 7200L)
        val now = d2.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val progress = rangeGoal(
            state.settings.copy(dailyGoal = 60),
            listOf(d1, d2, d3),
            totals,
            emptyList(),
            now,
        )
        assertEquals(75, progress)
        assertNull(
            rangeGoal(state.settings.copy(goalType = GoalType.NONE), listOf(d1), totals, emptyList(), now),
        )
    }
}
