package com.pomodorotimer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.pomodorotimer.app.R

/** Lucide outline icons, distributed under the ISC license in assets/licenses. */
internal object OutlineIcons {
    val Alarm: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_alarm_clock)
    val Stats: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_chart_no_axes_column)
    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_settings_2)
    val Cloud: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_cloud)
    val Pause: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_pause)
    val Play: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_play)
    val Volume: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_volume_2)
    val Bell: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_bell)
    val Target: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_target)
    val Timer: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_timer)
    val Calendar: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_calendar_days)
    val Clock: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_clock_3)
    val Hourglass: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_hourglass)
    val Repeat: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_repeat_2)
    val Theme: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_palette)
    val Language: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_languages)
    val Shield: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_shield)
    val Blocklist: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_shield_ban)
    val Grid: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_grid_2x2)
    val Music: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_music_2)
    val Download: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_download)
    val Previous: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_chevron_left)
    val Next: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_chevron_right)
    val Delete: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_outline_trash_2)
}
