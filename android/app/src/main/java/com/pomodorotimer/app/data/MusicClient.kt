package com.pomodorotimer.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Catalogue entry of one user-owned track. */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val revision: Int,
)

/**
 * Cloud music: the catalogue is read from `music_tracks`, the audio is streamed through the
 * `download_music` RPC (which enforces ownership and the revision check) and cached on disk.
 */
class MusicClient(
    private val baseUrl: String,
    private val anonKey: String,
) {

    val configured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun tracks(session: CloudClient.Session): List<MusicTrack> = withContext(Dispatchers.IO) {
        val raw = get(
            "/rest/v1/music_tracks?select=id,title,artist,audio_revision,sort_order" +
                "&user_id=eq.${session.userId}&enabled=eq.true&order=sort_order",
            session.accessToken,
        )
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            MusicTrack(
                id = o.optString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                revision = o.optInt("audio_revision", 1),
            )
        }
    }

    suspend fun download(
        context: Context,
        session: CloudClient.Session,
        track: MusicTrack,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File? = withContext(Dispatchers.IO) {
        val account = java.util.UUID.fromString(session.userId).toString()
        val trackId = java.util.UUID.fromString(track.id).toString()
        val dir = File(context.filesDir, "music/$account").apply { mkdirs() }
        val file = File(dir, "$trackId-${track.revision}.mp3")
        if (file.exists() && file.length() > 0) return@withContext file

        val connection = (URL(
            baseUrl.trimEnd('/') +
                "/rest/v1/rpc/download_music?track_id=${track.id}&revision=${track.revision}",
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Accept", "audio/mpeg")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val partial = File.createTempFile("track-", ".part", dir)
            try {
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var copied = 0L
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(copied, total)
                        }
                    }
                }
                ensureActive()
                if (total > 0 && partial.length() != total) return@withContext null
                if (partial.length() == 0L || !partial.renameTo(file)) return@withContext null
                file
            } finally {
                if (partial.exists()) partial.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun get(path: String, token: String): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Music catalogue unavailable")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
