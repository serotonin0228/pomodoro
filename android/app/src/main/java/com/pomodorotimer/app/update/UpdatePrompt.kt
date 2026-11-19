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
internal fun UpdatePrompt(english: Boolean) {
    if (BuildConfig.DEBUG) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<AppUpdate?>(null) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        try {
            available = AppUpdates.latest(BuildConfig.VERSION_NAME)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The timer works offline. Retry on the next launch.
        }
    }

    val update = available ?: return
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
        onDismissRequest = { if (!downloading) available = null },
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
                onClick = { available = null },
            )
        }
    }
}
