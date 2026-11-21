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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import java.io.File

/** Downloads survive dismissal of the music panel for as long as the app process remains alive. */
object MusicDownloads {
    data class Status(val active: Boolean, val progress: Float? = null, val failed: Boolean = false)
    val statuses = mutableStateMapOf<String, Status>()
    var queueRevision by mutableIntStateOf(0)
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private val jobs = mutableMapOf<String, Job>()
    private var generation = 0L

    fun cancelAll() {
        generation++
        val activeJobs = jobs.values.toList()
        jobs.clear()
        activeJobs.forEach { it.cancel() }
        statuses.clear()
        queueRevision++
    }

    fun cancel(trackId: String) {
        jobs.remove(trackId)?.cancel()
        statuses.remove(trackId)
    }

    fun start(context: Context, track: MusicTrack, session: CloudClient.Session, catalogue: List<MusicTrack>) {
        if (statuses[track.id]?.active == true) return
        val app = context.applicationContext
        if (WorkspaceStore(app).activeAccount() != session.userId) return
        val requestGeneration = generation
        statuses[track.id] = Status(active = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ownJob = coroutineContext[Job]
            fun current() = requestGeneration == generation && jobs[track.id] === ownJob
            try {
                val client = MusicClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
                var lastUpdate = 0L
                val file = client.download(app, session, track) { bytes, total ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= 100 || (total > 0 && bytes >= total)) {
                        lastUpdate = now
                        handler.post {
                            if (current() && statuses[track.id]?.active == true) {
                                statuses[track.id] = Status(true, if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null)
                            }
                        }
                    }
                } ?: throw IllegalStateException("Download failed")
                if (!current() || WorkspaceStore(app).activeAccount() != session.userId) return@launch
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
                if (current()) statuses.remove(track.id)
                throw cancel
            } catch (_: Exception) {
                if (current()) statuses[track.id] = Status(active = false, failed = true)
            } finally {
                if (current()) jobs.remove(track.id)
            }
        }
        jobs[track.id] = job
        job.start()
    }
}
