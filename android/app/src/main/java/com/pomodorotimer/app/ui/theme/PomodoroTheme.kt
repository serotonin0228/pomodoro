package com.pomodorotimer.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.pomodorotimer.app.domain.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/**
 * Miuix is the only design system in this app: colours, typography, controls and materials all come
 * from it, so the surface behaves like a HyperOS system app rather than a themed Compose sample.
 *
 * The palette is generated from Miuix's own tonal-spot scheme; the user's theme choice only decides
 * light/dark, never a hand-picked accent colour.
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
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = Color(0xFF087CFF),
            paletteStyle = ThemePaletteStyle.TonalSpot,
            colorSpec = ThemeColorSpec.Spec2021,
        )
    }
    MiuixTheme(
        controller = controller,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            ) {
                content()
            }
        },
    )
}
