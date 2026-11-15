package com.serotonin.pomodoro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Focus screen: one big clock, one primary action, one secondary action.
 *
 * All controls come from Miuix; the screen owns no timer state of its own, it only renders
 * [WorkspaceController.state] against the current wall clock.
 */
@Composable
fun TimerScreen(
    controller: WorkspaceController,
    now: Long,
) {
    val state = controller.state
    val timer = state.timer
    val text = LocalUiText.current
    var confirmingEnd by remember { mutableStateOf(false) }

    val phaseLabel = if (timer.phase == Phase.FOCUS) text.focus else text.rest
    val observed = elapsed(timer, now)
    val canDiscard = timer.phase == Phase.FOCUS && observed <= 300

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = phaseLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = clock(displaySeconds(timer, now)),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 68.sp,
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
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (timer.mode == Mode.COUNTDOWN && timer.duration > 0) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = (observed.toFloat() / timer.duration.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

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

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = if (timer.mode == Mode.COUNTDOWN) text.switchToStopwatch else text.switchToCountdown,
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

    OverlayDialog(
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
