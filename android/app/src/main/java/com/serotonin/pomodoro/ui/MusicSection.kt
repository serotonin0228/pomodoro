package com.serotonin.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.serotonin.pomodoro.BuildConfig
import com.serotonin.pomodoro.data.CloudClient
import com.serotonin.pomodoro.data.MusicClient
import com.serotonin.pomodoro.data.MusicPlayer
import com.serotonin.pomodoro.data.MusicTrack
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Background music: two switches plus the user's cloud catalogue.
 *
 * Tracks come from the same Supabase project as sync; picking one downloads the current audio
 * revision once and then plays from the local cache.
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
    var playing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text.sectionMusic, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.musicEnabled)
            Switch(
                checked = settings.musicEnabled,
                onCheckedChange = { value -> controller.updateSettings { it.copy(musicEnabled = value) } },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.musicDuringBreak)
            Switch(
                checked = settings.musicDuringBreak,
                onCheckedChange = { value -> controller.updateSettings { it.copy(musicDuringBreak = value) } },
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            text = "刷新曲库",
            onClick = {
                val session = loadCloudSession(context) ?: run {
                    status = text.syncLocalOnly
                    return@TextButton
                }
                scope.launch {
                    status = "…"
                    tracks = runCatching { client.tracks(session) }.getOrElse { emptyList() }
                    status = if (tracks.isEmpty()) text.noTracks else "${tracks.size}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (status.isNotBlank()) {
            Text(status, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }

        tracks.forEach { track ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title)
                    if (track.artist.isNotBlank()) {
                        Text(track.artist, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                TextButton(
                    text = if (playing) "停" else "播放",
                    onClick = {
                        val session = loadCloudSession(context) ?: return@TextButton
                        if (playing) {
                            MusicPlayer.stop()
                            playing = false
                            return@TextButton
                        }
                        scope.launch {
                            val file = runCatching { client.download(context, session, track) }.getOrNull()
                            if (file == null) {
                                status = text.downloadFailed
                            } else {
                                MusicPlayer.play(file) { status = text.downloadFailed }
                                playing = true
                                controller.updateSettings { it.copy(musicTrackId = track.id) }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The music section shares the sign-in state written by [CloudSection]. */
private fun loadCloudSession(context: Context): CloudClient.Session? {
    val prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    val raw = prefs.getString("session", null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        CloudClient.Session(o.getString("userId"), o.getString("access"), o.optString("refresh"))
    }.getOrNull()
}
