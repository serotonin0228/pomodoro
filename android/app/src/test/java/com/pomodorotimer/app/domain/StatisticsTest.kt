package com.pomodorotimer.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTest {
    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 1, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun record(id: String, start: Long, end: Long, deleted: Boolean = false) =
        FocusRecord(id, Mode.COUNTDOWN, start, end, (end - start) / 1000, listOf(Segment(start, end)), deleted)

    @Test fun calendarRangesCrossYearAndRespectLeapYear() {
        val today = LocalDate.of(2024, 1, 1)
        assertEquals(LocalDate.of(2023, 12, 25), statisticsDays(today, StatisticsPeriod.WEEK, -1).first())
        assertEquals(29, statisticsDays(LocalDate.of(2024, 2, 20), StatisticsPeriod.MONTH, 0).size)
        assertEquals(LocalDate.of(2023, 12, 31), statisticsDays(today, StatisticsPeriod.DAY, -1).single())
    }

    @Test fun overlappingAndCrossMidnightIntervalsCountOnce() {
        val records = listOf(record("a", at(1, 23), at(2, 2)), record("b", at(2, 0), at(2, 1)), record("deleted", at(2, 4), at(2, 5), true))
        val hours = hourlyTotals(records, listOf(LocalDate.of(2026, 1, 2)), zone)
        assertEquals(3600L, hours[0])
        assertEquals(3600L, hours[1])
        assertEquals(7200L, hours.sum())
        assertEquals(listOf("a", "b"), recordsInDays(records, listOf(LocalDate.of(2026, 1, 2)), zone).map { it.id })
    }

    @Test fun emptyRangesHaveNoRecordsOrHours() {
        assertEquals(emptyList<FocusRecord>(), recordsInDays(emptyList(), emptyList(), zone))
        assertEquals(0L, hourlyTotals(emptyList(), emptyList(), zone).sum())
    }
    @Test fun customRangesIncludeBothEndpointsAndLimitWindowSize() {
        val today = java.time.LocalDate.of(2026, 11, 21)
        assertEquals(listOf(today.minusDays(1), today), customStatisticsDays(today.minusDays(1), today, today))
        assertEquals(366, customStatisticsDays(today.minusDays(365), today, today).size)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            customStatisticsDays(today.minusDays(366), today, today)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            customStatisticsDays(today, today.plusDays(1), today)
        }
    }

}
