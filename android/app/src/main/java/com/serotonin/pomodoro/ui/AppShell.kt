package com.serotonin.pomodoro.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.serotonin.pomodoro.domain.Clock
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Weeks

/**
 * Application shell in the KernelSU-Style-UI-Kit layout:
 *
 * a collapsible Miuix [TopAppBar] driven by [MiuixScrollBehavior], page content as a [LazyColumn]
 * with 12dp gutters, and the Miuix [NavigationBar] at the bottom. Nothing here is a Material
 * component.
 */
@Composable
fun AppShell(controller: WorkspaceController) {
    var now by remember { mutableLongStateOf(Clock.now()) }
    val text = LocalUiText.current
    val context = LocalContext.current
    val scrollBehavior = remember { MiuixScrollBehavior() }

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

    val title = when (controller.tab) {
        WorkspaceTab.TIMER -> text.appName
        WorkspaceTab.STATS -> text.stats
        WorkspaceTab.SETTINGS -> text.settings
    }

    Scaffold(
        topBar = { TopAppBar(title = title, scrollBehavior = scrollBehavior) },
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
                NavigationBarItem(
                    selected = controller.tab == WorkspaceTab.SETTINGS,
                    onClick = { controller.tab = WorkspaceTab.SETTINGS },
                    icon = MiuixIcons.Settings,
                    label = text.settings,
                )
            }
        },
    ) { padding ->
        when (controller.tab) {
            WorkspaceTab.TIMER -> TimerScreen(controller, now, scrollBehavior, padding)
            WorkspaceTab.STATS -> StatsScreen(controller, now, scrollBehavior, padding)
            WorkspaceTab.SETTINGS -> SettingsScreen(controller, scrollBehavior, padding)
        }
    }
}
