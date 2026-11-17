package com.pomodorotimer.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import com.pomodorotimer.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Downloads survive dismissal of the music panel for as long as the app process remains alive. */
object MusicDownloads {
    data class Status(val active: Boolean, val progress: Float? = null, val failed: Boolean = false)
    val statuses = mutableStateMapOf<String, Status>()
    var queueRevision by mutableIntStateOf(0)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context, track: MusicTrack, session: CloudClient.Session, catalogue: List<MusicTrack>) {
        if (statuses[track.id]?.active == true) return
        val app = context.applicationContext
        statuses[track.id] = Status(active = true)
        scope.launch {
            try {
                val client = MusicClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
                var lastUpdate = 0L
                val file = client.download(app, session, track) { bytes, total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= 100 || (total > 0 && bytes >= total)) {
                        lastUpdate = now
                        handler.post {
                            if (statuses[track.id]?.active == true) {
                                statuses[track.id] = Status(true, if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null)
                            }
                        }
                    }
                } ?: throw IllegalStateException("Download failed")
                val existing = MusicRepository.loadQueue(app)
                val entries = existing?.trackIds?.mapIndexedNotNull { i, id ->
                    val path = existing.paths.getOrNull(i)
                    val title = existing.titles.getOrNull(i)
                    if (id != track.id && path != null && title != null && File(path).isFile) Triple(id, path, title) else null
                }.orEmpty() + Triple(track.id, file.absolutePath, track.title)
                val order = catalogue.mapIndexed { i, item -> item.id to i }.toMap()
                val sorted = entries.sortedBy { order[it.first] ?: Int.MAX_VALUE }
                MusicRepository.saveQueue(app, MusicRepository.Queue(sorted.map { it.second }, sorted.map { it.third }, sorted.map { it.first }))
                queueRevision++
                statuses.remove(track.id)
            } catch (cancel: CancellationException) {
                statuses.remove(track.id)
                throw cancel
            } catch (_: Exception) {
                statuses[track.id] = Status(active = false, failed = true)
            }
        }
    }
}
