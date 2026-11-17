package com.pomodorotimer.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Progress of the selected range as a percentage, ported from `src/goal.ts`.
 *
 * Days after today never count toward the target, so looking at a whole month does not make today
 * look like a failure. Returns null when goals are disabled.
 */
fun rangeGoal(
    settings: Settings,
    days: List<LocalDate>,
    totals: Map<String, Long>,
    records: List<FocusRecord>,
    now: Long,
): Int? {
    if (settings.goalType == GoalType.NONE) return null
    val today = dayKey(now)
    val eligible = days.filter { it.toString() <= today }
    if (eligible.isEmpty()) return 0
    val keys = eligible.map { it.toString() }.toSet()

    if (settings.goalType == GoalType.DAILY_SESSIONS) {
        val count = records.count { !it.deleted && dayKey(it.start) in keys }
        val target = maxOf(1, settings.dailySessions) * eligible.size
        return (count.toDouble() / target * 100).roundToInt()
    }

    val seconds = eligible.sumOf { totals[it.toString()] ?: 0L }

    if (settings.goalType == GoalType.WEEKLY_MINUTES) {
        val weeks = eligible.map { day ->
            val dayOfWeek = day.dayOfWeek.value // Monday = 1 ... Sunday = 7
            day.minusDays(((dayOfWeek + 6) % 7).toLong()).toString()
        }.toSet()
        val target = maxOf(1, settings.weeklyMinutes) * 60.0 * weeks.size
        return (seconds / target * 100).roundToInt()
    }

    val target = maxOf(1, settings.dailyGoal) * 60.0 * eligible.size
    return (seconds / target * 100).roundToInt()
}
