package com.pomodorotimer.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.data.CloudClient
import com.pomodorotimer.app.data.MusicClient
import com.pomodorotimer.app.data.MusicCoordinator
import com.pomodorotimer.app.data.MusicQueue
import com.pomodorotimer.app.data.MusicRepository
import com.pomodorotimer.app.data.MusicTrack
import com.pomodorotimer.app.domain.MusicMode
import com.pomodorotimer.app.service.MusicService
import com.pomodorotimer.app.ui.i18n.LocalUiText
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Background music panel.
 *
 * "Refresh" downloads the whole catalogue once and stores the prepared queue, so starting a focus
 * session needs no network. Playback itself lives in [MusicService], which owns the media session
 * and the lock-screen controls; this panel only prepares the queue and reports status.
 */
@Composable
fun MusicSection(controller: WorkspaceController) {
    val text = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = controller.state.settings
    val client = remember { MusicClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(MusicRepository.loadQueue(context) != null) }

    fun prepareQueue(list: List<MusicTrack>, session: CloudClient.Session, onDone: () -> Unit) {
        if (list.isEmpty()) {
            status = text.noTracks
            return
        }
        busy = true
        scope.launch {
            val order = MusicQueue.order(list.size, settings.musicMode)
            val paths = ArrayList<String>()
            val titles = ArrayList<String>()
            val ids = ArrayList<String>()
            var failed = 0
            order.forEachIndexed { position, trackIndex ->
                val track = list[trackIndex]
                status = "${position + 1}/${order.size}"
                val file = runCatching { client.download(context, session, track) }.getOrNull()
                if (file == null) {
                    failed++
                } else {
                    paths.add(file.absolutePath)
                    titles.add(track.title)
                    ids.add(track.id)
                }
            }
            if (paths.isNotEmpty()) {
                MusicRepository.saveQueue(
                    context,
                    MusicRepository.Queue(paths.toList(), titles.toList(), ids.toList()),
                )
                prepared = true
            }
            status = if (failed > 0) text.downloadFailed else "${paths.size}"
            busy = false
            onDone()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text.sectionMusic, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))

        SwitchRow(text.musicEnabled, settings.musicEnabled) { value ->
            controller.updateSettings { it.copy(musicEnabled = value) }
        }
        SwitchRow(text.musicDuringBreak, settings.musicDuringBreak) { value ->
            controller.updateSettings { it.copy(musicDuringBreak = value) }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicMode.entries.forEach { mode ->
                TextButton(
                    text = when (mode) {
                        MusicMode.SINGLE -> text.modeSingle
                        MusicMode.SEQUENCE -> text.modeSequence
                        MusicMode.SHUFFLE -> text.modeShuffle
                    },
                    onClick = { controller.updateSettings { it.copy(musicMode = mode) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            text = text.musicRefresh,
            onClick = {
                val session = loadCloudSession(context) ?: run {
                    status = text.syncLocalOnly
                    return@TextButton
                }
                busy = true
                scope.launch {
                    tracks = runCatching { client.tracks(session) }.getOrElse { emptyList() }
                    busy = false
                    prepareQueue(tracks, session) {}
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )

        if (status.isNotBlank()) {
            Text(status, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }

        tracks.forEach { track ->
            val selected = track.id == settings.musicTrackId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (selected) "● ${track.title}" else track.title)
                    if (track.artist.isNotBlank()) {
                        Text(track.artist, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                TextButton(
                    text = if (selected) text.play else text.play,
                    onClick = {
                        controller.updateSettings { it.copy(musicTrackId = track.id) }
                        val session = loadCloudSession(context) ?: return@TextButton
                        if (!prepared) {
                            prepareQueue(tracks, session) {}
                        } else {
                            val queue = MusicRepository.loadQueue(context) ?: return@TextButton
                            MusicService.play(
                                context,
                                queue.paths,
                                queue.titles,
                                MusicRepository.startIndex(queue, track.id),
                            )
                        }
                    },
                    enabled = !busy,
                )
            }
        }

        if (prepared) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                text = text.stop,
                onClick = {
                    MusicCoordinator.stop(context)
                    MusicRepository.saveQueue(context, MusicRepository.Queue(emptyList(), emptyList(), emptyList()))
                    prepared = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun loadCloudSession(context: Context): CloudClient.Session? {
    val prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    val raw = prefs.getString("session", null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        CloudClient.Session(o.getString("userId"), o.getString("access"), o.optString("refresh"))
    }.getOrNull()
}
