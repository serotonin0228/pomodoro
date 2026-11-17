package com.pomodorotimer.app.ui

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.data.CloudClient
import com.pomodorotimer.app.data.MusicDownloads
import com.pomodorotimer.app.data.MusicClient
import com.pomodorotimer.app.data.MusicLibraryCache
import com.pomodorotimer.app.data.MusicCoordinator
import com.pomodorotimer.app.data.MusicQueue
import com.pomodorotimer.app.data.MusicRepository
import com.pomodorotimer.app.data.MusicTrack
import com.pomodorotimer.app.domain.MusicMode
import com.pomodorotimer.app.service.MusicPlayback
import com.pomodorotimer.app.service.MusicService
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Forward
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

    val session = loadCloudSession(context)
    var tracks by remember(session?.userId) {
        val cached = session?.let { MusicLibraryCache.load(context, it.userId) }
        val queue = MusicRepository.loadQueue(context)
        val legacy = queue?.trackIds?.mapIndexedNotNull { index, id ->
            queue.titles.getOrNull(index)?.let { MusicTrack(id, it, "", 0) }
        } ?: emptyList()
        mutableStateOf(cached ?: legacy)
    }
    val queueRevision = MusicDownloads.queueRevision
    val queue = remember(queueRevision) { MusicRepository.loadQueue(context) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(session?.userId) {
        val current = session ?: return@LaunchedEffect
        if (MusicLibraryCache.needsRefresh(context, current.userId)) {
            runCatching { client.tracks(current) }.onSuccess { remote ->
                MusicLibraryCache.save(context, current.userId, remote)
                tracks = remote
            }
        }
    }

    fun localPath(track: MusicTrack): String? {
        val old = queue?.trackIds?.indexOf(track.id) ?: -1
        val path = if (old >= 0) queue?.paths?.getOrNull(old) else null
        val matching = path != null && (track.revision == 0 || path.endsWith("-${track.revision}.mp3"))
        return path?.takeIf { matching && java.io.File(it).isFile && java.io.File(it).length() > 0 }
    }

    fun playTrack(track: MusicTrack) {
        if (MusicDownloads.statuses[track.id]?.active == true) return
        if (localPath(track) != null && queue != null) {
            controller.updateSettings { it.copy(musicTrackId = track.id) }
            val index = queue.trackIds.indexOf(track.id)
            if (index >= 0 && index < queue.paths.size) {
                if (MusicPlayback.path == queue.paths[index] && !MusicPlayback.playing) MusicService.resume(context)
                else MusicService.play(context, queue.paths, queue.titles, index)
            }
        } else {
            val current = loadCloudSession(context)
            if (current == null) { status = text.syncLocalOnly; return }
            status = ""
            MusicDownloads.start(context, track, current, tracks)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = text.musicEnabled,
            checked = settings.musicEnabled,
            onCheckedChange = { value -> controller.updateSettings { it.copy(musicEnabled = value) } },
        )
        SwitchPreference(
            title = text.musicDuringBreak,
            checked = settings.musicDuringBreak,
            onCheckedChange = { value -> controller.updateSettings { it.copy(musicDuringBreak = value) } },
        )
        Spacer(Modifier.height(8.dp))
        TabRowWithContour(
            listOf(text.modeSingle, text.modeSequence, text.modeShuffle),
            settings.musicMode.ordinal,
            { index -> controller.updateSettings { it.copy(musicMode = MusicMode.entries[index]) } },
            modifier = Modifier.fillMaxWidth(),
            indication = LocalIndication.current,
        )
        Spacer(Modifier.height(8.dp))
        tracks.forEach { track ->
            val downloaded = localPath(track) != null
            val download = MusicDownloads.statuses[track.id]
            val isPlayingTrack = MusicPlayback.playing && localPath(track) == MusicPlayback.path
            val detail = when {
                download?.active == true && download.progress != null -> "${(download.progress * 100).toInt()}%"
                download?.active == true -> text.loading
                download?.failed == true -> text.downloadFailed
                else -> track.artist
            }
            BasicComponent(
                title = track.title,
                summary = detail.takeIf { it.isNotBlank() },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                enabled = download?.active != true,
                onClick = { if (isPlayingTrack) MusicService.pause(context) else playTrack(track) },
                endActions = {
                    if (download?.active == true) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = download.progress, size = 22.dp, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        IconButton(onClick = { if (isPlayingTrack) MusicService.pause(context) else playTrack(track) }, modifier = Modifier.size(48.dp)) {
                            Icon(if (isPlayingTrack) OutlineIcons.Pause else if (downloaded) OutlineIcons.Play else OutlineIcons.Download, contentDescription = if (isPlayingTrack) text.pause else if (downloaded) text.play else text.download, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                },
            )
        }
        if (status.isNotBlank()) {
            Text(status, color = colorScheme.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
