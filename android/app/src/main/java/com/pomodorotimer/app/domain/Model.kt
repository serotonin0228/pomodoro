package com.pomodorotimer.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Ported 1:1 from the reference implementation (`src/model.ts` in the React Native app).
 *
 * Time is always absolute epoch milliseconds. A running timer stores only the moment its current
 * segment started plus the seconds accumulated before it, so a paused timer and a device that was
 * suspended for hours produce exactly the same numbers.
 */
enum class Mode { COUNTDOWN, STOPWATCH }

enum class Phase { FOCUS, BREAK }

enum class TimerStatus { IDLE, RUNNING, PAUSED, COMPLETED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class GoalType { DAILY_MINUTES, DAILY_SESSIONS, WEEKLY_MINUTES, NONE }

enum class MusicMode { SINGLE, SEQUENCE, SHUFFLE }

/** One contiguous span of focused time. */
data class Segment(val start: Long, val end: Long)

data class TimerSession(
    val id: String,
    val mode: Mode,
    val phase: Phase,
    val status: TimerStatus,
    /** Target length in seconds (meaningful for COUNTDOWN). */
    val duration: Long,
    /** Seconds accumulated before the current running segment started. */
    val elapsed: Long,
    /** Epoch millis when the current running segment started, null when not running. */
    val startedAt: Long?,
    val createdAt: Long,
    val device: String,
)

data class FocusRecord(
    val id: String,
    val mode: Mode,
    val start: Long,
    val end: Long,
    /** Focused seconds after segment merging. */
    val seconds: Long,
    val segments: List<Segment>,
    val deleted: Boolean = false,
)

data class Settings(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val rounds: Int = 4,
    val dailyGoal: Int = 120,
    val goalType: GoalType = GoalType.DAILY_MINUTES,
    val dailySessions: Int = 4,
    val weeklyMinutes: Int = 600,
    val streakMinutes: Int = 1,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notifications: Boolean = true,
    val autoBreak: Boolean = false,
    val autoFocus: Boolean = false,
    val sound: Boolean = true,
    val musicDuringBreak: Boolean = false,
    val musicEnabled: Boolean = true,
    val musicTrack: Int = 0,
    val musicTrackId: String? = null,
    val musicMode: MusicMode = MusicMode.SINGLE,
    val language: Language = Language.ZH,
) {
    companion object {
        val Default = Settings()
    }
}

data class AppState(
    val timer: TimerSession,
    val records: List<FocusRecord>,
    val settings: Settings,
    val segments: List<Segment>,
    val round: Int,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- identity

fun uid(now: Long = Clock.now()): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    val tail = (0 until 10).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    return "${now.toString(36)}-$tail"
}

// ---------------------------------------------------------------- construction

fun newTimer(
    device: String,
    settings: Settings = Settings.Default,
    mode: Mode = Mode.COUNTDOWN,
    phase: Phase = Phase.FOCUS,
    round: Int = 0,
    now: Long = Clock.now(),
): TimerSession {
    val minutes = if (phase == Phase.FOCUS) {
        settings.focusMinutes
    } else if (round > 0 && round % settings.rounds == 0) {
        settings.longBreakMinutes
    } else {
        settings.breakMinutes
    }
    return TimerSession(
        id = uid(now),
        mode = mode,
        phase = phase,
        status = TimerStatus.IDLE,
        duration = minutes * 60L,
        elapsed = 0L,
        startedAt = null,
        createdAt = now,
        device = device,
    )
}

fun initialState(device: String, now: Long = Clock.now()): AppState = AppState(
    timer = newTimer(device, Settings.Default, now = now),
    records = emptyList(),
    settings = Settings.Default,
    segments = emptyList(),
    round = 0,
    updatedAt = now,
)

// ---------------------------------------------------------------- reading the clock

/** Seconds observed so far, including the live segment. */
fun elapsed(t: TimerSession, now: Long = Clock.now()): Long {
    val live = if (t.status == TimerStatus.RUNNING && t.startedAt != null) {
        (now - t.startedAt) / 1000
    } else {
        0L
    }
    return maxOf(0L, t.elapsed + live)
}

fun remaining(t: TimerSession, now: Long = Clock.now()): Long = maxOf(0L, t.duration - elapsed(t, now))

/** What the big label shows: elapsed for a stopwatch, remaining (rounded up) for a countdown. */
fun displaySeconds(t: TimerSession, now: Long): Long =
    if (t.mode == Mode.STOPWATCH) elapsed(t, now) else ceilDiv(remaining(t, now), 1L)

fun clock(seconds: Long): String {
    val s = maxOf(0L, seconds)
    return if (s >= 3600) {
        "${s / 3600}:${((s / 60) % 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    } else {
        "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }
}

private fun ceilDiv(value: Long, unit: Long): Long {
    if (unit <= 1L) return value
    return (value + unit - 1) / unit
}

// ---------------------------------------------------------------- transitions

/**
 * Closes the live segment. A countdown is clipped at its deadline, which is what makes a
 * suspended device report exactly the configured length instead of the wall-clock overshoot.
 */
fun closeSegments(s: AppState, now: Long): List<Segment> {
    val timer = s.timer
    if (timer.status != TimerStatus.RUNNING || timer.startedAt == null) return s.segments
    val startedAt = timer.startedAt
    val end = if (timer.mode == Mode.COUNTDOWN) {
        minOf(now, startedAt + maxOf(0L, timer.duration - timer.elapsed) * 1000)
    } else {
        now
    }
    return s.segments + Segment(startedAt, maxOf(startedAt, end))
}

fun pauseState(s: AppState, now: Long = Clock.now()): AppState {
    val observed = elapsed(s.timer, now)
    val capped = if (s.timer.mode == Mode.COUNTDOWN) minOf(observed, s.timer.duration) else observed
    return s.copy(
        segments = closeSegments(s, now),
        timer = s.timer.copy(status = TimerStatus.PAUSED, elapsed = capped, startedAt = null),
        updatedAt = now,
    )
}

/**
 * Finishes or discards the session. Focus time of at least one second becomes a record exactly
 * once; rest time never does.
 */
fun finishState(s: AppState, now: Long = Clock.now(), completed: Boolean = false): AppState {
    val segments = closeSegments(s, now)
    val seconds = segments.sumOf { (it.end - it.start) / 1000L }
    var records = s.records
    val alreadyRecorded = records.any { it.id == s.timer.id }
    if (s.timer.phase == Phase.FOCUS && seconds >= 1 && !alreadyRecorded && segments.isNotEmpty()) {
        records = records + FocusRecord(
            id = s.timer.id,
            mode = s.timer.mode,
            start = segments.first().start,
            end = segments.last().end,
            seconds = seconds,
            segments = segments,
        )
    }
    val focusClosed = s.timer.phase == Phase.FOCUS && seconds >= 1
    return s.copy(
        records = records,
        segments = emptyList(),
        round = s.round + if (focusClosed) 1 else 0,
        timer = s.timer.copy(
            status = if (completed) TimerStatus.COMPLETED else TimerStatus.IDLE,
            elapsed = if (completed) s.timer.duration else 0L,
            startedAt = null,
            id = if (completed) s.timer.id else uid(now),
        ),
        updatedAt = now,
    )
}

// ---------------------------------------------------------------- statistics

private val zone: ZoneId get() = ZoneId.systemDefault()

/** Local calendar day key, e.g. `2026-11-06`, in the device time zone. */
fun dayKey(instant: Long): String = LocalDate.ofInstant(Instant.ofEpochMilli(instant), zone).toString()

/**
 * Merge overlapping intervals before summing, so two devices recording the same session can never
 * double-count, then split each merged interval at local midnight.
 */
fun dailyTotals(records: List<FocusRecord>): Map<String, Long> {
    val intervals = records.asSequence()
        .filter { !it.deleted }
        .flatMap { it.segments.asSequence() }
        .sortedBy { it.start }
        .toList()

    val merged = ArrayList<Segment>()
    for (x in intervals) {
        val last = merged.lastOrNull()
        if (last != null && x.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, x.end))
        } else {
            merged.add(x)
        }
    }

    val totals = LinkedHashMap<String, Long>()
    for (x in merged) {
        var cursor = x.start
        while (cursor < x.end) {
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(cursor), zone)
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = minOf(nextMidnight, x.end)
            val key = date.toString()
            totals[key] = (totals[key] ?: 0L) + (end - cursor) / 1000L
            cursor = end
        }
    }
    return totals
}

/**
 * Deterministic merge of two copies of the same history: a deleted record stays deleted, and
 * unique intervals from both sides survive while duplicates collapse.
 */
fun mergeRecords(local: List<FocusRecord>, remote: List<FocusRecord>): List<FocusRecord> {
    val map = LinkedHashMap<String, FocusRecord>()
    local.forEach { map[it.id] = it }
    for (r in remote) {
        val existing = map[r.id]
        if (existing == null) {
            map[r.id] = r
            continue
        }
        val merged = ArrayList<Segment>()
        for (x in (existing.segments + r.segments).sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && x.start <= last.end) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, x.end))
            } else {
                merged.add(x)
            }
        }
        map[r.id] = r.copy(
            start = minOf(existing.start, r.start),
            end = maxOf(existing.end, r.end),
            segments = merged,
            seconds = merged.sumOf { (it.end - it.start) / 1000L },
            deleted = existing.deleted || r.deleted,
        )
    }
    return map.values.sortedBy { it.start }
}

/** Human summary, e.g. `1小时30分`; the UI layer localizes it through [formatDurationLocalized]. */
fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        minutes >= 60 -> buildString {
            append(minutes / 60).append("小时")
            if (minutes % 60 != 0L) append(minutes % 60).append("分")
        }
        minutes > 0 -> "${minutes}分钟"
        else -> "${seconds}秒"
    }
}
