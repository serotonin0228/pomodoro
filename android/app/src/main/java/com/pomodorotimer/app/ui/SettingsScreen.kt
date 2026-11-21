package com.pomodorotimer.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.domain.GoalType
import com.pomodorotimer.app.domain.Language
import com.pomodorotimer.app.domain.ThemeMode
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Settings page built exclusively from Miuix preferences.
 *
 * Grouping rows into cards, putting an icon in `startAction` and letting the preference component
 * draw its own press feedback is what makes the page look like a HyperOS settings screen instead of
 * a stack of custom rows.
 */
@Composable
fun SettingsScreen(
    controller: WorkspaceController,
    scrollBehavior: ScrollBehavior,
    padding: PaddingValues,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val settings = controller.state.settings
    val text = LocalUiText.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 720.dp).fillMaxWidth().fillMaxSize()
            .padding(horizontal = 16.dp)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsHeading(text.sectionCloud) }
        item { CloudCard(controller) }

        item { SettingsHeading(text.sectionBehaviour) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = text.autoBreak,
                    startAction = { PreferenceIcon(OutlineIcons.Pause) },
                    checked = settings.autoBreak,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(autoBreak = value) }
                    },
                )
                SwitchPreference(
                    title = text.autoFocus,
                    startAction = { PreferenceIcon(OutlineIcons.Play) },
                    checked = settings.autoFocus,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(autoFocus = value) }
                    },
                )
                SwitchPreference(
                    title = text.sound,
                    startAction = { PreferenceIcon(OutlineIcons.Volume) },
                    checked = settings.sound,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(sound = value) }
                    },
                )
                SwitchPreference(
                    title = text.notifications,
                    startAction = { PreferenceIcon(OutlineIcons.Bell) },
                    checked = settings.notifications,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(notifications = value) }
                    },
                )
            }
        }

        item { SettingsHeading(text.sectionGoal) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(
                        text.goalDailyMinutes,
                        text.goalDailySessions,
                        text.goalWeeklyMinutes,
                        text.goalOff,
                    ),
                    selectedIndex = settings.goalType.ordinal,
                    title = text.goalLabel,
                    startAction = { PreferenceIcon(OutlineIcons.Target) },
                    onSelectedIndexChange = { index ->
                        val type = GoalType.entries.getOrElse(index) { GoalType.DAILY_MINUTES }
                        controller.updateSettings { it.copy(goalType = type) }
                    },
                )
                when (settings.goalType) {
                    GoalType.DAILY_MINUTES -> SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                        title = text.goalDailyMinutes,
                    startAction = { PreferenceIcon(OutlineIcons.Timer) },
                        value = settings.dailyGoal.toFloat(),
                        onValueChange = { value ->
                            controller.updateSettings { it.copy(dailyGoal = value.toInt()) }
                        },
                        valueRange = 15f..1440f,
                        steps = 94,
                        valueText = text.minutes(settings.dailyGoal.toLong()),
                    )

                    GoalType.DAILY_SESSIONS -> SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                        title = text.goalDailySessions,
                    startAction = { PreferenceIcon(OutlineIcons.Target) },
                        value = settings.dailySessions.toFloat(),
                        onValueChange = { value ->
                            controller.updateSettings { it.copy(dailySessions = value.toInt()) }
                        },
                        valueRange = 1f..12f,
                        steps = 10,
                        valueText = "${settings.dailySessions}",
                    )

                    GoalType.WEEKLY_MINUTES -> SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                        title = text.goalWeeklyMinutes,
                    startAction = { PreferenceIcon(OutlineIcons.Calendar) },
                        value = settings.weeklyMinutes.toFloat(),
                        onValueChange = { value ->
                            controller.updateSettings { it.copy(weeklyMinutes = value.toInt()) }
                        },
                        valueRange = 60f..3000f,
                        steps = 48,
                        valueText = text.minutes(settings.weeklyMinutes.toLong()),
                    )

                    GoalType.NONE -> Unit
                }
            }
        }

        item { SettingsHeading(text.sectionTiming) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                    title = text.focusLength,
                    startAction = { PreferenceIcon(OutlineIcons.Alarm) },
                    value = settings.focusMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(focusMinutes = value.toInt()) }
                    },
                    valueRange = 5f..90f,
                    steps = 16,
                    valueText = text.minutes(settings.focusMinutes.toLong()),
                )
                SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                    title = text.shortBreak,
                    startAction = { PreferenceIcon(OutlineIcons.Hourglass) },
                    value = settings.breakMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(breakMinutes = value.toInt()) }
                    },
                    valueRange = 1f..30f,
                    steps = 28,
                    valueText = text.minutes(settings.breakMinutes.toLong()),
                )
                SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                    title = text.longBreak,
                    startAction = { PreferenceIcon(OutlineIcons.Clock) },
                    value = settings.longBreakMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(longBreakMinutes = value.toInt()) }
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    valueText = text.minutes(settings.longBreakMinutes.toLong()),
                )
                SliderPreference(
                    bottomAction = { Spacer(Modifier.height(14.dp)) },
                    title = text.rounds,
                    startAction = { PreferenceIcon(OutlineIcons.Repeat) },
                    value = settings.rounds.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(rounds = value.toInt()) }
                    },
                    valueRange = 2f..8f,
                    steps = 5,
                    valueText = "${settings.rounds}",
                )
            }
        }

        item { SettingsHeading(text.sectionAppearance) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = if (settings.language == Language.EN) "Keep timer screen awake" else "计时页面保持常亮",
                    startAction = { PreferenceIcon(OutlineIcons.Clock) },
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
                OverlayDropdownPreference(
                    items = listOf(text.themeSystem, text.themeLight, text.themeDark),
                    selectedIndex = settings.theme.ordinal,
                    title = text.themeLabel,
                    startAction = { PreferenceIcon(OutlineIcons.Theme) },
                    onSelectedIndexChange = { index ->
                        val mode = ThemeMode.entries.getOrElse(index) { ThemeMode.SYSTEM }
                        controller.updateSettings { it.copy(theme = mode) }
                    },
                )
                OverlayDropdownPreference(
                    items = listOf("中文", "English"),
                    selectedIndex = if (settings.language == Language.ZH) 0 else 1,
                    title = text.languageLabel,
                    startAction = { PreferenceIcon(OutlineIcons.Language) },
                    onSelectedIndexChange = { index ->
                        val language = if (index == 0) Language.ZH else Language.EN
                        controller.updateSettings { it.copy(language = language) }
                    },
                )
            }
        }

        item { SettingsHeading(text.sectionAppLimits) }
        item { FocusGuardCard(controller) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                HistoryBackup(controller, settings.language == Language.EN)
                if (!com.pomodorotimer.app.BuildConfig.DEBUG) ArrowPreference(
                    title = if (settings.language == Language.EN) "Check for updates" else "检查更新",
                    summary = "v${com.pomodorotimer.app.BuildConfig.VERSION_NAME}",
                    startAction = { PreferenceIcon(OutlineIcons.Cloud) },
                    onClick = onCheckUpdates,
                )
            }
        }
    }
    }
}

@Composable
private fun SettingsHeading(title: String) {
    Text(title, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurfaceVariantSummary)
}
