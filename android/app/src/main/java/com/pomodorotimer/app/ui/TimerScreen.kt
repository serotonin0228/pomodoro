package com.pomodorotimer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorotimer.app.data.MusicRepository
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.Phase
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.clock
import com.pomodorotimer.app.domain.displaySeconds
import com.pomodorotimer.app.domain.elapsed
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Focus page.
 *
 * One large Miuix card owns the clock, so the most important number is also the biggest element on
 * screen; actions sit underneath in the same visual rhythm the system apps use.
 */
@Composable
fun TimerScreen(
    controller: WorkspaceController,
    now: Long,
    scrollBehavior: ScrollBehavior,
    padding: PaddingValues,
) {
    val state = controller.state
    val timer = state.timer
    val text = LocalUiText.current
    val context = LocalContext.current
    val queue = remember(state.settings.musicTrackId, state.settings.musicEnabled) { MusicRepository.loadQueue(context) }
    val track = queue?.titles?.getOrNull(MusicRepository.startIndex(queue, state.settings.musicTrackId))
    var confirmingEnd by remember { mutableStateOf(false) }
    var showingMusic by remember { mutableStateOf(false) }
    var takeoverBusy by remember { mutableStateOf(false) }
    var takeoverError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val observed = elapsed(timer, now)
    val canDiscard = timer.phase == Phase.FOCUS && observed <= 300

    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.TopCenter,
    ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabRowWithContour(
                    listOf(text.countdownMode, text.stopwatchMode),
                    timer.mode.ordinal,
                    { index ->
                        val mode = Mode.entries[index]
                        if (timer.mode != mode) controller.changeMode(mode)
                    },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                BoxWithConstraints(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val clockText = clock(displaySeconds(timer, now))
                    val fontScale = LocalDensity.current.fontScale
                    Text(
                        text = clockText,
                        fontSize = (maxWidth.value / (clockText.length * 0.64f) / fontScale).coerceIn(32f, 100f).sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(20.dp))
                Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    if (timer.mode == Mode.COUNTDOWN) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(state.settings.rounds.coerceIn(1, 8)) { index ->
                                Text("●", fontSize = 8.sp, color = if (index < state.round % state.settings.rounds.coerceAtLeast(1)) colorScheme.primary else colorScheme.onSurfaceVariantSummary.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(42.dp))
                Button(
                    onClick = {
                        if (controller.isRemoteActive) {
                            if (!takeoverBusy) scope.launch {
                                takeoverBusy = true
                                takeoverError = !controller.takeover()
                                takeoverBusy = false
                            }
                        } else controller.primary()
                    },
                    modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (controller.isRemoteActive) (if (state.settings.language == com.pomodorotimer.app.domain.Language.EN) "Take over here" else "在此设备接管") else when (timer.status) { TimerStatus.RUNNING -> text.pause; TimerStatus.PAUSED -> text.resume; TimerStatus.COMPLETED -> text.nextPhase; TimerStatus.IDLE -> text.start }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    if (!controller.isRemoteActive && (controller.isActive || controller.isCompleted)) {
                        TextButton(text = text.end, onClick = { confirmingEnd = true }, modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth())
                    }
                }
                Spacer(Modifier.weight(1f))
                Card(onClick = { showingMusic = true }, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = OutlineIcons.Music, contentDescription = null, tint = colorScheme.primary)
                        Text(track ?: text.sectionMusic, modifier = Modifier.weight(1f), color = colorScheme.onSurface, fontSize = 15.sp, maxLines = 1)
                        Icon(OutlineIcons.Next, contentDescription = null, tint = colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
    }

    WindowDialog(show = takeoverError, title = if (state.settings.language == com.pomodorotimer.app.domain.Language.EN) "Unable to take over" else "接管失败", summary = if (state.settings.language == com.pomodorotimer.app.domain.Language.EN) "Check your cloud connection and try again." else "请检查云同步连接后重试。", onDismissRequest = { takeoverError = false }) {
        TextButton(text = if (state.settings.language == com.pomodorotimer.app.domain.Language.EN) "OK" else "确定", onClick = { takeoverError = false }, modifier = Modifier.fillMaxWidth())
    }

    WindowDialog(
        show = showingMusic,
        title = text.sectionMusic,
        onDismissRequest = { showingMusic = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            MusicCard(controller)
        }
    }

    WindowDialog(
        show = confirmingEnd,
        title = if (timer.phase == Phase.FOCUS) text.endFocusTitle else text.endBreakTitle,
        summary = if (timer.phase == Phase.FOCUS) text.endFocusBody else text.endBreakBody,
        onDismissRequest = { confirmingEnd = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    confirmingEnd = false
                    controller.endAndSave()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (timer.phase == Phase.FOCUS) text.endAndSave else text.endBreak)
            }
            if (canDiscard) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = text.discard,
                    onClick = {
                        confirmingEnd = false
                        controller.endAndDiscard()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
