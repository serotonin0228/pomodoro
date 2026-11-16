package com.serotonin.pomodoro.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Layout tokens distilled from the KernelSU-Style-UI-Kit Miuix screens (see docs/STYLE.md).
 *
 * They exist so a page cannot drift: every screen uses the same gutter, card spacing and icon size
 * instead of inventing its own numbers.
 */
object Dimens {
    /** Horizontal page gutter, applied once by the scrolling list. */
    val Gutter = 12.dp

    /** Gap between two cards. */
    val CardSpacing = 12.dp

    /** Inner padding of a text card. */
    val CardInner = 16.dp

    /** Leading icon in a preference row. */
    val ActionIcon = 24.dp

    /** Gap between a leading icon and its title. */
    val ActionIconGap = 12.dp

    /** Watermark icon in a status card. */
    val WatermarkIcon = 96.dp

    /** Title size of a status card. */
    val StatusTitle = 22.sp

    /** Secondary text inside cards. */
    val StatusBody = 15.sp
}
