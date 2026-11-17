package com.pomodorotimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.pomodorotimer.app.domain.ThemeMode
import com.pomodorotimer.app.service.TimerActionReceiver
import com.pomodorotimer.app.service.MusicService
import com.pomodorotimer.app.ui.AppShell
import com.pomodorotimer.app.ui.WorkspaceController
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.i18n.uiText
import com.pomodorotimer.app.ui.theme.PomodoroTheme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class MainActivity : ComponentActivity() {

    private var controller: WorkspaceController? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            controller?.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val workspace = remember { WorkspaceController(applicationContext) }
            SideEffect { controller = workspace }

            val systemDark = isSystemInDarkTheme()
            val dark = workspace.state.settings.theme == ThemeMode.DARK ||
                (workspace.state.settings.theme == ThemeMode.SYSTEM && systemDark)

            // Edge-to-edge with transparent system bars, exactly like the Miuix reference apps.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { dark },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val language = workspace.state.settings.language
            CompositionLocalProvider(LocalUiText provides uiText(language)) {
                PomodoroTheme(themeMode = workspace.state.settings.theme) {
                    AppShell(controller = workspace)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(TimerActionReceiver.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
        controller?.refresh()
        controller?.requestSync()
        MusicService.refreshNotificationSettings()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onPause()
    }
}
