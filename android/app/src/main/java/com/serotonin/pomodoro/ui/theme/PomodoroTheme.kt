package com.serotonin.pomodoro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.serotonin.pomodoro.domain.ThemeMode

/**
 * The app is themed exclusively through Miuix, so every surface, text and control inherits the
 * MIUI / HyperOS colour scheme instead of a Material one.
 */
@Composable
fun PomodoroTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val mode = when (themeMode) {
        ThemeMode.SYSTEM -> ColorSchemeMode.System
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
    }
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller, content = content)
}
