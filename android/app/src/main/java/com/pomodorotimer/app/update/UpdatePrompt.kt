package com.pomodorotimer.app.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pomodorotimer.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

@Composable
internal fun UpdatePrompt(english: Boolean, sessionActive: Boolean, request: Int) {
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("update_prompt", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<AppUpdate?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var checkStatus by remember { mutableStateOf<CheckStatus?>(null) }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    val allowInstallation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val apk = downloaded
        if (apk != null && context.packageManager.canRequestPackageInstalls()) {
            failed = runCatching { install(apk) }.isFailure
        }
    }

    fun requestInstall(apk: File) {
        failed = runCatching {
            if (context.packageManager.canRequestPackageInstalls()) {
                install(apk)
            } else {
                allowInstallation.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }
        }.isFailure
    }

    LaunchedEffect(request) {
        val manual = request > 0
        if (manual) checkStatus = CheckStatus.CHECKING
        try {
            val candidate = AppUpdates.latest(BuildConfig.VERSION_NAME)
            val snoozedUntil = preferences.getLong("snooze_${candidate?.version}", 0L)
            if (candidate != null && (manual || System.currentTimeMillis() >= snoozedUntil)) {
                val cached = AppUpdates.cachedApk(context, candidate)
                downloaded = cached
                checkStatus = null
                available = candidate
            } else if (manual) {
                checkStatus = CheckStatus.UP_TO_DATE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (manual) checkStatus = CheckStatus.FAILED
        }
    }

    val status = checkStatus
    if (status != null) {
        val summary = when (status) {
            CheckStatus.CHECKING -> if (english) "Checking…" else "正在检查…"
            CheckStatus.UP_TO_DATE -> if (english) "You’re up to date." else "已是最新版本。"
            CheckStatus.FAILED -> if (english) "Could not check for updates." else "暂时无法检查更新。"
        }
        WindowDialog(
            show = true,
            title = if (english) "App update" else "应用更新",
            summary = summary,
            onDismissRequest = { checkStatus = null },
        ) {
            TextButton(text = if (english) "Close" else "关闭", onClick = { checkStatus = null }, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    val update = available ?: return
    if (sessionActive && request == 0 && !downloading) return

    fun snooze() {
        preferences.edit().putLong("snooze_${update.version}", System.currentTimeMillis() + 24 * 60 * 60 * 1000L).apply()
        available = null
    }

    val message = when {
        failed -> if (english) "Update failed. Try again." else "更新失败，请重试。"
        downloading -> if (english) "Downloading · $progress%" else "正在下载 · $progress%"
        downloaded != null -> if (english) "The update is ready to install." else "新版已下载，可以安装。"
        else -> if (english) "Version ${update.version} is available." else "已发现新版本 ${update.version}。"
    }
    WindowDialog(
        show = true,
        title = if (english) "App update" else "应用更新",
        summary = message,
        onDismissRequest = { if (!downloading) snooze() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = if (downloaded != null) {
                    if (english) "Install" else "安装"
                } else {
                    if (english) "Download update" else "下载更新"
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val apk = downloaded
                    if (apk != null) {
                        requestInstall(apk)
                    } else if (!downloading) {
                        downloading = true
                        failed = false
                        progress = 0
                        scope.launch {
                            try {
                                val file = AppUpdates.download(context, update) { fraction ->
                                    progress = (fraction * 100).toInt()
                                }
                                downloaded = file
                                requestInstall(file)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                failed = true
                            } finally {
                                downloading = false
                            }
                        }
                    }
                },
            )
            TextButton(
                text = if (english) "Later" else "稍后",
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
                onClick = { snooze() },
            )
        }
    }
}

private enum class CheckStatus { CHECKING, UP_TO_DATE, FAILED }
