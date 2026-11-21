package com.pomodorotimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.view.WindowManager
import com.pomodorotimer.app.ui.WorkspaceTab
import com.pomodorotimer.app.domain.ThemeMode
import com.pomodorotimer.app.domain.Language
import com.pomodorotimer.app.update.UpdatePrompt
import com.pomodorotimer.app.focus.FocusGuardDiagnostics
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

    companion object {
        internal const val ACTION_RETURN_TO_TIMER = "com.pomodorotimer.app.action.RETURN_TO_TIMER"
    }

    private var controller: WorkspaceController? = null
    private var openTimerWhenReady = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            controller?.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTimerWhenReady = intent.action == ACTION_RETURN_TO_TIMER
        val filter = IntentFilter(TimerActionReceiver.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val workspace = remember { WorkspaceController(applicationContext) }
            SideEffect {
                controller = workspace
                if (openTimerWhenReady) showTimer(workspace)
            }

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val devicePrefs = remember { getSharedPreferences("device_preferences", MODE_PRIVATE) }
            var keepScreenOn by remember { mutableStateOf(devicePrefs.getBoolean("keep_screen_on", false)) }
            var updateRequest by remember { mutableIntStateOf(0) }
            val screenOn = keepScreenOn && workspace.isRunning && workspace.tab == WorkspaceTab.TIMER
            DisposableEffect(screenOn) {
                if (screenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            val language = workspace.state.settings.language
            CompositionLocalProvider(LocalUiText provides uiText(language)) {
                PomodoroTheme(themeMode = workspace.state.settings.theme) {
                    AppShell(
                        controller = workspace,
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOn = { enabled ->
                            keepScreenOn = enabled
                            devicePrefs.edit().putBoolean("keep_screen_on", enabled).apply()
                        },
                        onCheckUpdates = { updateRequest++ },
                    )
                    UpdatePrompt(english = language == Language.EN, sessionActive = workspace.isActive, request = updateRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_RETURN_TO_TIMER) {
            val workspace = controller
            if (workspace == null) openTimerWhenReady = true else showTimer(workspace)
        }
    }

    private fun showTimer(workspace: WorkspaceController) {
        openTimerWhenReady = false
        workspace.tab = WorkspaceTab.TIMER
        FocusGuardDiagnostics.returns++
    }

    override fun onResume() {
        super.onResume()
        controller?.refresh()
        controller?.requestSync()
        MusicService.refreshNotificationSettings()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onDestroy()
    }
}
