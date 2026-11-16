package com.serotonin.pomodoro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.domain.GoalType
import com.serotonin.pomodoro.domain.Language
import com.serotonin.pomodoro.domain.ThemeMode
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import com.serotonin.pomodoro.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
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
) {
    val settings = controller.state.settings
    val text = LocalUiText.current

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
            Card(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    title = text.focusLength,
                    value = settings.focusMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(focusMinutes = value.toInt()) }
                    },
                    valueRange = 5f..90f,
                    steps = 16,
                    valueText = text.minutes(settings.focusMinutes.toLong()),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Alarm,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                )
                SliderPreference(
                    title = text.shortBreak,
                    value = settings.breakMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(breakMinutes = value.toInt()) }
                    },
                    valueRange = 1f..30f,
                    steps = 28,
                    valueText = text.minutes(settings.breakMinutes.toLong()),
                )
                SliderPreference(
                    title = text.longBreak,
                    value = settings.longBreakMinutes.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(longBreakMinutes = value.toInt()) }
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    valueText = text.minutes(settings.longBreakMinutes.toLong()),
                )
                SliderPreference(
                    title = text.rounds,
                    value = settings.rounds.toFloat(),
                    onValueChange = { value ->
                        controller.updateSettings { it.copy(rounds = value.toInt()) }
                    },
                    valueRange = 2f..8f,
                    steps = 5,
                    valueText = "${settings.rounds}",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Stopwatch,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = text.autoBreak,
                    checked = settings.autoBreak,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(autoBreak = value) }
                    },
                )
                SwitchPreference(
                    title = text.autoFocus,
                    checked = settings.autoFocus,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(autoFocus = value) }
                    },
                )
                SwitchPreference(
                    title = text.sound,
                    checked = settings.sound,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(sound = value) }
                    },
                )
                SwitchPreference(
                    title = text.notifications,
                    checked = settings.notifications,
                    onCheckedChange = { value ->
                        controller.updateSettings { it.copy(notifications = value) }
                    },
                )
            }
        }

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
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Alarm,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                    onSelectedIndexChange = { index ->
                        val type = GoalType.entries.getOrElse(index) { GoalType.DAILY_MINUTES }
                        controller.updateSettings { it.copy(goalType = type) }
                    },
                )
                when (settings.goalType) {
                    GoalType.DAILY_MINUTES -> SliderPreference(
                        title = text.goalDailyMinutes,
                        value = settings.dailyGoal.toFloat(),
                        onValueChange = { value ->
                            controller.updateSettings { it.copy(dailyGoal = value.toInt()) }
                        },
                        valueRange = 15f..480f,
                        steps = 31,
                        valueText = text.minutes(settings.dailyGoal.toLong()),
                    )

                    GoalType.DAILY_SESSIONS -> SliderPreference(
                        title = text.goalDailySessions,
                        value = settings.dailySessions.toFloat(),
                        onValueChange = { value ->
                            controller.updateSettings { it.copy(dailySessions = value.toInt()) }
                        },
                        valueRange = 1f..12f,
                        steps = 10,
                        valueText = "${settings.dailySessions}",
                    )

                    GoalType.WEEKLY_MINUTES -> SliderPreference(
                        title = text.goalWeeklyMinutes,
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

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(text.themeSystem, text.themeLight, text.themeDark),
                    selectedIndex = settings.theme.ordinal,
                    title = text.themeLabel,
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Theme,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                    onSelectedIndexChange = { index ->
                        val mode = ThemeMode.entries.getOrElse(index) { ThemeMode.SYSTEM }
                        controller.updateSettings { it.copy(theme = mode) }
                    },
                )
                OverlayDropdownPreference(
                    items = listOf("中文", "English"),
                    selectedIndex = if (settings.language == Language.ZH) 0 else 1,
                    title = text.languageLabel,
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Translate,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                    onSelectedIndexChange = { index ->
                        val language = if (index == 0) Language.ZH else Language.EN
                        controller.updateSettings { it.copy(language = language) }
                    },
                )
            }
        }

        item { FocusGuardCard(controller) }

        item { MusicCard(controller) }

        item { CloudCard(controller) }
    }
}
