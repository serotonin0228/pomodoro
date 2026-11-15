package com.serotonin.pomodoro.data

import android.content.Context
import com.serotonin.pomodoro.domain.AppState
import com.serotonin.pomodoro.domain.Phase
import com.serotonin.pomodoro.domain.TimerStatus
import com.serotonin.pomodoro.service.MusicService
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the prepared playback queue and keeps it in step with the timer.
 *
 * The queue is a list of cached files, so going from "focus started" to "audio playing" needs no
 * network and no download — that is what makes background playback reliable.
 */
object MusicRepository {

    private const val PREFS = "music"
    private const val KEY_QUEUE = "queue"

    data class Queue(val paths: List<String>, val titles: List<String>, val trackIds: List<String>)

    fun saveQueue(context: Context, queue: Queue) {
        val json = JSONObject().apply {
            put("paths", JSONArray(queue.paths))
            put("titles", JSONArray(queue.titles))
            put("ids", JSONArray(queue.trackIds))
        }
        prefs(context).edit().putString(KEY_QUEUE, json.toString()).apply()
    }

    fun loadQueue(context: Context): Queue? {
        val raw = prefs(context).getString(KEY_QUEUE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Queue(
                paths = json.getJSONArray("paths").toStringList(),
                titles = json.getJSONArray("titles").toStringList(),
                trackIds = json.optJSONArray("ids")?.toStringList() ?: emptyList(),
            )
        }.getOrNull()
    }

    /** The stored queue is already in play order, so the position is simply the id's index. */
    fun startIndex(queue: Queue, trackId: String?): Int {
        if (trackId == null) return 0
        val index = queue.trackIds.indexOf(trackId)
        return if (index < 0) 0 else index.coerceIn(0, (queue.paths.size - 1).coerceAtLeast(0))
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { optString(it) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Turns timer state into playback commands.
 *
 * Rules: music plays only while a focus session runs (or during breaks when the user opted in), and
 * pausing/finishing the timer pauses the music instead of stopping it, so resuming continues the
 * same track.
 */
object MusicCoordinator {

    @Volatile
    private var active = false

    fun sync(context: Context, state: AppState) {
        val queue = MusicRepository.loadQueue(context)
        if (queue == null || queue.paths.isEmpty()) {
            if (active) {
                MusicService.pause(context)
                active = false
            }
            return
        }

        val timer = state.timer
        val running = timer.status == TimerStatus.RUNNING
        val allowedPhase = timer.phase == Phase.FOCUS || state.settings.musicDuringBreak
        val shouldPlay = state.settings.musicEnabled && running && allowedPhase

        if (shouldPlay && !active) {
            val start = MusicRepository.startIndex(queue, state.settings.musicTrackId)
            MusicService.play(context, queue.paths, queue.titles, start)
            active = true
        } else if (!shouldPlay && active) {
            MusicService.pause(context)
            active = false
        }
    }

    /** User pressed stop: forget the prepared queue so the timer cannot restart it. */
    fun stop(context: Context) {
        MusicService.stop(context)
        active = false
    }

    fun isActive(): Boolean = active
}
