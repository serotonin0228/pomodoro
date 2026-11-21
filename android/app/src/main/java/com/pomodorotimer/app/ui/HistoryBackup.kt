package com.pomodorotimer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.data.StateCodec
import com.pomodorotimer.app.domain.FocusRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.ByteArrayOutputStream

private const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

@Composable
internal fun HistoryBackup(controller: WorkspaceController, english: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<FocusRecord>?>(null) }
    var accountAtSelection by remember { mutableStateOf<String?>(null) }
    var exportSnapshot by remember { mutableStateOf<List<FocusRecord>>(emptyList()) }
    val selectBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                pending = withContext(Dispatchers.IO) {
                    val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_BACKUP_BYTES)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    StateCodec.decodeHistory(bytes.toString(Charsets.UTF_8))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = if (english) "Could not read this backup." else "无法读取这份备份。"
            } finally {
                busy = false
            }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) {
            exportSnapshot = emptyList()
            return@rememberLauncherForActivityResult
        }
        val records = exportSnapshot
        exportSnapshot = emptyList()
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = StateCodec.encodeHistory(records).toByteArray(Charsets.UTF_8)
                    require(bytes.size <= MAX_BACKUP_BYTES)
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
                }
                message = if (english) "Backup saved." else "备份已保存。"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = if (english) "Could not save the backup." else "无法保存备份。"
            } finally {
                busy = false
            }
        }
    }
    val title = if (english) "History backup" else "专注记录备份"
    ArrowPreference(title = title, onClick = { message = null; open = true })
    WindowDialog(
        show = open,
        title = title,
        summary = if (pending != null) {
            if (english) "Merge these records into the current workspace?" else "将这些记录合并到当前工作区？"
        } else message,
        onDismissRequest = { if (!busy) { pending = null; open = false } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val history = pending
            if (history != null) {
                TextButton(text = if (english) "Merge records" else "合并记录", modifier = Modifier.fillMaxWidth(), onClick = {
                    message = runCatching { controller.restoreHistory(history, accountAtSelection) }.fold(
                        onSuccess = { if (english) "Added $it records." else "已添加 $it 条记录。" },
                        onFailure = { if (english) "The active account changed." else "当前账号已切换。" },
                    )
                    pending = null
                })
                TextButton(text = if (english) "Cancel" else "取消", modifier = Modifier.fillMaxWidth(), onClick = { pending = null })
            } else {
                TextButton(text = if (english) "Save backup" else "保存备份", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    exportSnapshot = controller.state.records
                    saveBackup.launch("pomodoro-history.json")
                })
                TextButton(text = if (english) "Restore backup" else "恢复备份", enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    accountAtSelection = controller.account
                    selectBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
            }
        }
    }
}
