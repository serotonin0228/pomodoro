package com.pomodorotimer.app.domain

import java.time.LocalDate
import java.time.ZoneId

/** Calendar-aligned ranges, independent of daylight-saving transitions. */
enum class StatisticsPeriod { DAY, WEEK, MONTH }

fun statisticsDays(today: LocalDate, period: StatisticsPeriod, offset: Int): List<LocalDate> {
    val start = when (period) {
        StatisticsPeriod.DAY -> today.plusDays(offset.toLong())
        StatisticsPeriod.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(offset.toLong())
        StatisticsPeriod.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong())
    }
    val count = when (period) {
        StatisticsPeriod.DAY -> 1
        StatisticsPeriod.WEEK -> 7
        StatisticsPeriod.MONTH -> start.lengthOfMonth()
    }
    return (0 until count).map { start.plusDays(it.toLong()) }
}

fun customStatisticsDays(start: LocalDate, end: LocalDate, today: LocalDate): List<LocalDate> {
    val between = java.time.temporal.ChronoUnit.DAYS.between(start, end)
    require(end <= today && between in 0..365)
    return List(between.toInt() + 1) { start.plusDays(it.toLong()) }
}

fun recordsInDays(records: List<FocusRecord>, days: List<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): List<FocusRecord> {
    if (days.isEmpty()) return emptyList()
    val start = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = days.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return records.filter { record -> !record.deleted && record.segments.any { it.end > start && it.start < end } }
}

/** Merge overlaps before dividing focus time into local clock hours. */
fun hourlyTotals(records: List<FocusRecord>, days: List<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
    val totals = MutableList(24) { 0L }
    if (days.isEmpty()) return totals
    val start = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = days.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val intervals = records.asSequence().filter { !it.deleted }.flatMap { it.segments.asSequence() }
        .map { Segment(maxOf(start, it.start), minOf(end, it.end)) }.filter { it.end > it.start }.sortedBy { it.start }.toList()
    val merged = mutableListOf<Segment>()
    intervals.forEach { segment ->
        val last = merged.lastOrNull()
        if (last != null && segment.start <= last.end) merged[merged.lastIndex] = last.copy(end = maxOf(last.end, segment.end))
        else merged.add(segment)
    }
    merged.forEach { segment ->
        var cursor = segment.start
        while (cursor < segment.end) {
            val zoned = java.time.Instant.ofEpochMilli(cursor).atZone(zone)
            // Advancing on the instant timeline handles repeated and skipped local hours.
            val next = zoned.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val until = minOf(segment.end, next)
            if (until <= cursor) break
            totals[zoned.hour] += (until - cursor) / 1000
            cursor = until
        }
    }
    return totals
}
