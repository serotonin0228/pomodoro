package com.serotonin.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serotonin.pomodoro.domain.Mode
import com.serotonin.pomodoro.domain.Phase
import com.serotonin.pomodoro.domain.TimerStatus
import com.serotonin.pomodoro.domain.clock
import com.serotonin.pomodoro.domain.displaySeconds
import com.serotonin.pomodoro.domain.elapsed
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import com.serotonin.pomodoro.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Button
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
    var confirmingEnd by remember { mutableStateOf(false) }

    val phaseLabel = if (timer.phase == Phase.FOCUS) text.focus else text.rest
    val observed = elapsed(timer, now)
    val canDiscard = timer.phase == Phase.FOCUS && observed <= 300

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.Gutter)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = Dimens.CardInner),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = phaseLabel,
                        fontSize = 13.sp,
                        color = colorScheme.primary,
                        modifier = Modifier
                            .background(colorScheme.tertiaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = Dimens.CardSpacing, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = clock(displaySeconds(timer, now)),
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (timer.status == TimerStatus.RUNNING) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (timer.status) {
                            TimerStatus.RUNNING -> "${text.running} · ${text.round(state.round + 1)}"
                            TimerStatus.PAUSED -> text.paused
                            TimerStatus.COMPLETED -> text.completed
                            TimerStatus.IDLE -> if (timer.mode == Mode.COUNTDOWN) text.ready else text.countdownMode
                        },
                        fontSize = 15.sp,
                        color = if (timer.status == TimerStatus.RUNNING) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariantSummary
                        },
                    )
                    if (timer.mode == Mode.COUNTDOWN && timer.duration > 0) {
                        Spacer(Modifier.height(18.dp))
                        LinearProgressIndicator(
                            progress = (observed.toFloat() / timer.duration.toFloat()).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { controller.primary() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (timer.status) {
                        TimerStatus.RUNNING -> text.pause
                        TimerStatus.PAUSED -> text.resume
                        TimerStatus.COMPLETED -> text.nextPhase
                        TimerStatus.IDLE -> text.start
                    },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = if (timer.mode == Mode.COUNTDOWN) {
                        text.switchToStopwatch
                    } else {
                        text.switchToCountdown
                    },
                    onClick = {
                        controller.changeMode(
                            if (timer.mode == Mode.COUNTDOWN) Mode.STOPWATCH else Mode.COUNTDOWN,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = text.end,
                    onClick = { confirmingEnd = true },
                    modifier = Modifier.weight(1f),
                    enabled = controller.isActive || controller.isCompleted,
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text.summaryReady, color = colorScheme.onSurfaceVariantSummary)
                    Text(
                        text = if (timer.phase == Phase.FOCUS) {
                            text.round(state.round + 1)
                        } else {
                            text.rest
                        },
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
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
