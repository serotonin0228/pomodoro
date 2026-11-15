package com.serotonin.pomodoro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.domain.GoalType
import com.serotonin.pomodoro.domain.Language
import com.serotonin.pomodoro.domain.ThemeMode
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Settings, expressed only with Miuix controls. */
@Composable
fun SettingsSheet(
    show: Boolean,
    controller: WorkspaceController,
    onDismiss: () -> Unit,
) {
    val settings = controller.state.settings
    val text = LocalUiText.current

    OverlayDialog(
        show = show,
        title = text.settings,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(text.sectionTiming) {
                SliderRow(
                    label = text.focusLength,
                    value = settings.focusMinutes.toFloat(),
                    unit = text.unitMinutes,
                    range = 5f..90f,
                    steps = 16,
                ) { controller.updateSettings { s -> s.copy(focusMinutes = it) } }
                SliderRow(
                    label = text.shortBreak,
                    value = settings.breakMinutes.toFloat(),
                    unit = text.unitMinutes,
                    range = 1f..30f,
                    steps = 28,
                ) { controller.updateSettings { s -> s.copy(breakMinutes = it) } }
                SliderRow(
                    label = text.longBreak,
                    value = settings.longBreakMinutes.toFloat(),
                    unit = text.unitMinutes,
                    range = 5f..60f,
                    steps = 10,
                ) { controller.updateSettings { s -> s.copy(longBreakMinutes = it) } }
                SliderRow(
                    label = text.rounds,
                    value = settings.rounds.toFloat(),
                    unit = "",
                    range = 2f..8f,
                    steps = 5,
                ) { controller.updateSettings { s -> s.copy(rounds = it) } }
            }

            Section(text.sectionBehaviour) {
                SwitchRow(text.autoBreak, settings.autoBreak) {
                    controller.updateSettings { s -> s.copy(autoBreak = it) }
                }
                SwitchRow(text.autoFocus, settings.autoFocus) {
                    controller.updateSettings { s -> s.copy(autoFocus = it) }
                }
                SwitchRow(text.sound, settings.sound) {
                    controller.updateSettings { s -> s.copy(sound = it) }
                }
                SwitchRow(text.notifications, settings.notifications) {
                    controller.updateSettings { s -> s.copy(notifications = it) }
                }
            }

            Section(text.sectionGoal) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalType.entries.forEach { type ->
                        TextButton(
                            text = when (type) {
                                GoalType.DAILY_MINUTES -> text.goalDailyMinutes
                                GoalType.DAILY_SESSIONS -> text.goalDailySessions
                                GoalType.WEEKLY_MINUTES -> text.goalWeeklyMinutes
                                GoalType.NONE -> text.goalOff
                            },
                            onClick = { controller.updateSettings { s -> s.copy(goalType = type) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                when (settings.goalType) {
                    GoalType.DAILY_MINUTES -> SliderRow(
                        label = text.goalDailyMinutes,
                        value = settings.dailyGoal.toFloat(),
                        unit = text.unitMinutes,
                        range = 15f..480f,
                        steps = 31,
                    ) { controller.updateSettings { s -> s.copy(dailyGoal = it) } }

                    GoalType.DAILY_SESSIONS -> SliderRow(
                        label = text.goalDailySessions,
                        value = settings.dailySessions.toFloat(),
                        unit = "",
                        range = 1f..12f,
                        steps = 10,
                    ) { controller.updateSettings { s -> s.copy(dailySessions = it) } }

                    GoalType.WEEKLY_MINUTES -> SliderRow(
                        label = text.goalWeeklyMinutes,
                        value = settings.weeklyMinutes.toFloat(),
                        unit = text.unitMinutes,
                        range = 60f..3000f,
                        steps = 48,
                    ) { controller.updateSettings { s -> s.copy(weeklyMinutes = it) } }

                    GoalType.NONE -> Unit
                }
            }

            Section(text.sectionAppearance) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        TextButton(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> text.themeSystem
                                ThemeMode.LIGHT -> text.themeLight
                                ThemeMode.DARK -> text.themeDark
                            },
                            onClick = { controller.updateSettings { s -> s.copy(theme = mode) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.entries.forEach { language ->
                        TextButton(
                            text = if (language == Language.ZH) "中文" else "English",
                            onClick = { controller.updateSettings { s -> s.copy(language = language) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FocusGuardSection()

            MusicSection(controller = controller)

            CloudSection(controller = controller)

            TextButton(
                text = text.done,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Text("${value.toInt()} $unit", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
