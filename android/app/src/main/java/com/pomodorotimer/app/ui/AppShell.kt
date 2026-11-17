package com.pomodorotimer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.ui.i18n.LocalUiText
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
    val scrollBehavior = MiuixScrollBehavior()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        controller.ensureSystemState()
        if (controller.isActive) controller.toast = text.restored
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            controller.syncCloud()
            delay(15_000)
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
                    icon = OutlineIcons.Alarm,
                    label = text.timer,
                )
                NavigationBarItem(
                    selected = controller.tab == WorkspaceTab.STATS,
                    onClick = { controller.tab = WorkspaceTab.STATS },
                    icon = OutlineIcons.Stats,
                    label = text.stats,
                )
                NavigationBarItem(
                    selected = controller.tab == WorkspaceTab.SETTINGS,
                    onClick = { controller.tab = WorkspaceTab.SETTINGS },
                    icon = OutlineIcons.Settings,
                    label = text.settings,
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = controller.tab,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "page",
        ) { tab ->
            when (tab) {
                WorkspaceTab.TIMER -> TimerScreen(controller, now, scrollBehavior, padding)
                WorkspaceTab.STATS -> StatsScreen(controller, now, scrollBehavior, padding)
                WorkspaceTab.SETTINGS -> SettingsScreen(controller, scrollBehavior, padding)
            }
        }
    }
}
