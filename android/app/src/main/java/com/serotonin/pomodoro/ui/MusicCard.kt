package com.serotonin.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.BuildConfig
import com.serotonin.pomodoro.data.CloudClient
import com.serotonin.pomodoro.data.MusicClient
import com.serotonin.pomodoro.data.MusicCoordinator
import com.serotonin.pomodoro.data.MusicQueue
import com.serotonin.pomodoro.data.MusicRepository
import com.serotonin.pomodoro.data.MusicTrack
import com.serotonin.pomodoro.domain.MusicMode
import com.serotonin.pomodoro.service.MusicService
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import com.serotonin.pomodoro.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** Background music card: switches, play mode, library refresh and the prepared tracks. */
@Composable
fun MusicCard(controller: WorkspaceController) {
    val text = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = controller.state.settings
    val client = remember { MusicClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(MusicRepository.loadQueue(context) != null) }

    fun prepare(list: List<MusicTrack>, session: CloudClient.Session) {
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
                if (file == null) failed++ else {
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
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = text.musicEnabled,
            checked = settings.musicEnabled,
            onCheckedChange = { value -> controller.updateSettings { it.copy(musicEnabled = value) } },
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Music,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.ActionIconGap),
                    tint = colorScheme.onBackground,
                )
            },
        )
        SwitchPreference(
            title = text.musicDuringBreak,
            checked = settings.musicDuringBreak,
            onCheckedChange = { value -> controller.updateSettings { it.copy(musicDuringBreak = value) } },
        )
        OverlayDropdownPreference(
            items = listOf(text.modeSingle, text.modeSequence, text.modeShuffle),
            selectedIndex = settings.musicMode.ordinal,
            title = text.modeLabel,
            onSelectedIndexChange = { index ->
                val mode = MusicMode.entries.getOrElse(index) { MusicMode.SINGLE }
                controller.updateSettings { it.copy(musicMode = mode) }
            },
        )
        ArrowPreference(
            title = text.musicRefresh,
            summary = status.ifBlank { null },
            onClick = {
                val session = loadCloudSession(context)
                if (session == null) {
                    status = text.syncLocalOnly
                    return@ArrowPreference
                }
                busy = true
                scope.launch {
                    tracks = runCatching { client.tracks(session) }.getOrElse { emptyList() }
                    busy = false
                    prepare(tracks, session)
                }
            },
            enabled = !busy,
        )

        tracks.forEach { track ->
            ArrowPreference(
                title = track.title,
                summary = track.artist.ifBlank { null },
                endActions = {
                    TextButton(
                        text = text.play,
                        onClick = {
                            controller.updateSettings { it.copy(musicTrackId = track.id) }
                            val session = loadCloudSession(context) ?: return@TextButton
                            if (!prepared) {
                                prepare(tracks, session)
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
                },
                startAction = {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = colorScheme.onBackground,
                    )
                },
            )
        }

        if (prepared) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = text.stop,
                    onClick = {
                        MusicCoordinator.stop(context)
                        MusicRepository.saveQueue(
                            context,
                            MusicRepository.Queue(emptyList(), emptyList(), emptyList()),
                        )
                        prepared = false
                    },
                )
            }
        }
        if (status.isNotBlank() && tracks.isEmpty()) {
            Text(
                text = status,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
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
