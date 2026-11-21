package com.pomodorotimer.app.data

import com.pomodorotimer.app.domain.AppState
import com.pomodorotimer.app.domain.FocusRecord
import com.pomodorotimer.app.domain.GoalType
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.MusicMode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.Segment
import com.pomodorotimer.app.domain.Settings
import com.pomodorotimer.app.domain.ThemeMode
import com.pomodorotimer.app.domain.TimerSession
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.Language
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for the workspace snapshot.
 *
 * Field names match the React Native payload on purpose: the same document can be pushed to
 * Supabase, so a device still running the old app and a device running this rewrite read and
 * write exactly the same shape.
 */
object StateCodec {

    fun encode(state: AppState): String = JSONObject().apply {
        put("timer", timerToJson(state.timer))
        put("records", JSONArray().apply { state.records.forEach { put(recordToJson(it)) } })
        put("settings", settingsToJson(state.settings))
        put("segments", segmentsToJson(state.segments))
        put("round", state.round)
        put("updatedAt", state.updatedAt)
    }.toString()

    fun decode(text: String): AppState? = runCatching {
        val root = JSONObject(text)
        AppState(
            timer = timerFromJson(root.getJSONObject("timer")),
            records = root.optJSONArray("records").toRecords(),
            settings = settingsFromJson(root.optJSONObject("settings")),
            segments = root.optJSONArray("segments").toSegments(),
            round = root.optInt("round", 0),
            updatedAt = root.optLong("updatedAt", 0L),
        )
    }.getOrNull()

    // ------------------------------------------------------------ timer

    private fun timerToJson(t: TimerSession) = JSONObject().apply {
        put("id", t.id)
        put("mode", if (t.mode == Mode.COUNTDOWN) "countdown" else "stopwatch")
        put("phase", if (t.phase == Phase.FOCUS) "focus" else "break")
        put("status", t.status.name.lowercase())
        put("duration", t.duration)
        put("elapsed", t.elapsed)
        put("startedAt", t.startedAt ?: JSONObject.NULL)
        put("createdAt", t.createdAt)
        put("device", t.device)
    }

    private fun timerFromJson(o: JSONObject) = TimerSession(
        id = o.optString("id"),
        mode = if (o.optString("mode") == "stopwatch") Mode.STOPWATCH else Mode.COUNTDOWN,
        phase = if (o.optString("phase") == "break") Phase.BREAK else Phase.FOCUS,
        status = when (o.optString("status")) {
            "running" -> TimerStatus.RUNNING
            "paused" -> TimerStatus.PAUSED
            "completed" -> TimerStatus.COMPLETED
            else -> TimerStatus.IDLE
        },
        duration = o.optLong("duration", 1500),
        elapsed = o.optLong("elapsed", 0),
        startedAt = if (o.isNull("startedAt")) null else o.optLong("startedAt"),
        createdAt = o.optLong("createdAt", 0),
        device = o.optString("device"),
    )

    // ------------------------------------------------------------ records

    private fun recordToJson(r: FocusRecord) = JSONObject().apply {
        put("id", r.id)
        put("mode", if (r.mode == Mode.COUNTDOWN) "countdown" else "stopwatch")
        put("start", r.start)
        put("end", r.end)
        put("seconds", r.seconds)
        put("segments", segmentsToJson(r.segments))
        if (r.deleted) put("deleted", true)
    }

    private fun recordFromJson(o: JSONObject) = FocusRecord(
        id = o.optString("id"),
        mode = if (o.optString("mode") == "stopwatch") Mode.STOPWATCH else Mode.COUNTDOWN,
        start = o.optLong("start"),
        end = o.optLong("end"),
        seconds = o.optLong("seconds"),
        segments = o.optJSONArray("segments").toSegments(),
        deleted = o.optBoolean("deleted", false),
    )

    // ------------------------------------------------------------ segments

    private fun segmentsToJson(segments: List<Segment>) = JSONArray().apply {
        segments.forEach { segment ->
            put(JSONObject().put("start", segment.start).put("end", segment.end))
        }
    }

    private fun JSONArray?.toSegments(): List<Segment> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val o = optJSONObject(index) ?: return@mapNotNull null
            Segment(o.optLong("start"), o.optLong("end"))
        }
    }

    private fun JSONArray?.toRecords(): List<FocusRecord> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { recordFromJson(it) }
        }
    }

    // ------------------------------------------------------------ settings

    private fun settingsToJson(s: Settings) = JSONObject().apply {
        put("focusMinutes", s.focusMinutes)
        put("breakMinutes", s.breakMinutes)
        put("longBreakMinutes", s.longBreakMinutes)
        put("rounds", s.rounds)
        put("dailyGoal", s.dailyGoal)
        // Same literal names as the React Native payload, camelCase included.
        put(
            "goalType",
            when (s.goalType) {
                GoalType.DAILY_MINUTES -> "dailyMinutes"
                GoalType.DAILY_SESSIONS -> "dailySessions"
                GoalType.WEEKLY_MINUTES -> "weeklyMinutes"
                GoalType.NONE -> "none"
            },
        )
        put("dailySessions", s.dailySessions)
        put("weeklyMinutes", s.weeklyMinutes)
        put("streakMinutes", s.streakMinutes)
        put("theme", s.theme.name.lowercase())
        put("notifications", s.notifications)
        put("autoBreak", s.autoBreak)
        put("autoFocus", s.autoFocus)
        put("sound", s.sound)
        put("musicDuringBreak", s.musicDuringBreak)
        put("musicEnabled", s.musicEnabled)
        put("musicTrack", s.musicTrack)
        s.musicTrackId?.let { put("musicTrackId", it) }
        put("musicMode", s.musicMode.name.lowercase())
        put("language", if (s.language == Language.ZH) "zh" else "en")
    }

    private fun settingsFromJson(o: JSONObject?): Settings {
        val d = Settings.Default
        if (o == null) return d
        return Settings(
            focusMinutes = o.optInt("focusMinutes", d.focusMinutes),
            breakMinutes = o.optInt("breakMinutes", d.breakMinutes),
            longBreakMinutes = o.optInt("longBreakMinutes", d.longBreakMinutes),
            rounds = o.optInt("rounds", d.rounds),
            dailyGoal = o.optInt("dailyGoal", d.dailyGoal),
            goalType = when (o.optString("goalType")) {
                "dailySessions" -> GoalType.DAILY_SESSIONS
                "weeklyMinutes" -> GoalType.WEEKLY_MINUTES
                "none" -> GoalType.NONE
                else -> GoalType.DAILY_MINUTES
            },
            dailySessions = o.optInt("dailySessions", d.dailySessions),
            weeklyMinutes = o.optInt("weeklyMinutes", d.weeklyMinutes),
            streakMinutes = o.optInt("streakMinutes", d.streakMinutes),
            theme = when (o.optString("theme")) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            },
            notifications = o.optBoolean("notifications", d.notifications),
            autoBreak = o.optBoolean("autoBreak", d.autoBreak),
            autoFocus = o.optBoolean("autoFocus", d.autoFocus),
            sound = o.optBoolean("sound", d.sound),
            musicDuringBreak = o.optBoolean("musicDuringBreak", d.musicDuringBreak),
            musicEnabled = o.optBoolean("musicEnabled", d.musicEnabled),
            musicTrack = o.optInt("musicTrack", d.musicTrack),
            musicTrackId = o.optString("musicTrackId").takeIf { it.isNotEmpty() && it != "null" },
            language = if (o.optString("language") == "en") Language.EN else Language.ZH,
            musicMode = when (o.optString("musicMode")) {
                "sequence" -> MusicMode.SEQUENCE
                "shuffle" -> MusicMode.SHUFFLE
                else -> MusicMode.SINGLE
            },
        )
    }
}
