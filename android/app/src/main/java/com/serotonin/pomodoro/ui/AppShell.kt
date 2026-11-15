package com.serotonin.pomodoro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.serotonin.pomodoro.domain.Clock
import com.serotonin.pomodoro.domain.Phase
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Weeks

/**
 * Application shell: one scaffold, two destinations (timer / statistics) and the settings sheet.
 *
 * The single ticker lives here, so both screens read the same clock value and the controller can
 * resolve countdown completion in exactly one place.
 */
@Composable
fun AppShell(controller: WorkspaceController) {
    var now by remember { mutableLongStateOf(Clock.now()) }
    var showSettings by remember { mutableStateOf(false) }

    val text = LocalUiText.current
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        controller.ensureSystemState()
        if (controller.isActive) controller.toast = LocalUiText.current.restored
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val current = Clock.now()
            now = current
            controller.tick(current)
            delay(200)
        }
    }

    val phase = if (controller.state.timer.phase == Phase.FOCUS) text.focus else text.rest
    val subtitle = controller.toast
        ?: if (controller.isRunning) "$phase · ${text.round(controller.state.round + 1)}" else phase

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = text.appName,
                subtitle = subtitle,
                actions = {
                    TextButton(text = text.settings, onClick = { showSettings = true })
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = controller.tab == WorkspaceTab.TIMER,
                    onClick = { controller.tab = WorkspaceTab.TIMER },
                    icon = MiuixIcons.Alarm,
                    label = text.timer,
                )
                NavigationBarItem(
                    selected = controller.tab == WorkspaceTab.STATS,
                    onClick = { controller.tab = WorkspaceTab.STATS },
                    icon = MiuixIcons.Weeks,
                    label = text.stats,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (controller.tab) {
                WorkspaceTab.TIMER -> TimerScreen(controller = controller, now = now)
                WorkspaceTab.STATS -> StatsScreen(controller = controller, now = now)
            }
        }
    }

    SettingsSheet(
        show = showSettings,
        controller = controller,
        onDismiss = { showSettings = false },
    )
}
