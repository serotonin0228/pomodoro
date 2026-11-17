package com.pomodorotimer.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent catalogue, isolated per signed-in user. Audio files are stored separately. */
object MusicLibraryCache {
    private const val PREFS = "music_catalogue"
    private const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun load(context: Context, userId: String): List<MusicTrack>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tracks_$userId", null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                MusicTrack(item.getString("id"), item.getString("title"), item.optString("artist"), item.optInt("revision", 1))
            }
        }.getOrNull()
    }

    fun save(context: Context, userId: String, tracks: List<MusicTrack>, now: Long = System.currentTimeMillis()) {
        val data = JSONArray()
        tracks.forEach { data.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("revision", it.revision)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tracks_$userId", data.toString()).putLong("updated_$userId", now).apply()
    }

    fun needsRefresh(context: Context, userId: String, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.contains("tracks_$userId") || now - prefs.getLong("updated_$userId", 0L) >= REFRESH_INTERVAL_MS
    }
}
