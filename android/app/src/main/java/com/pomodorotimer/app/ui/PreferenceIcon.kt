package com.pomodorotimer.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun PreferenceIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp), tint = colorScheme.primary)
}
