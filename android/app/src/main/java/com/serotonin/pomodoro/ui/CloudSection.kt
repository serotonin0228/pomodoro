package com.serotonin.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.BuildConfig
import com.serotonin.pomodoro.data.CloudClient
import com.serotonin.pomodoro.domain.Clock
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Optional cloud sync.
 *
 * Accounts live in Supabase (the same deployment the React Native app uses), the workspace is one
 * document guarded by `save_workspace`'s optimistic revision check, and a conflict always resolves
 * by merging locally instead of overwriting either side.
 */
@Composable
fun CloudSection(controller: WorkspaceController) {
    val text = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("cloud", Context.MODE_PRIVATE) }
    val client = remember { CloudClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }

    var session by remember { mutableStateOf(loadSession(prefs)) }
    var status by remember {
        mutableStateOf(if (session == null) text.syncLocalOnly else text.syncOn)
    }
    var busy by remember { mutableStateOf(false) }
    val email = rememberTextFieldState()
    val password = rememberTextFieldState()

    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            status = runCatching { block() }.getOrElse { text.syncError }
            busy = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text.sectionCloud, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))
        Text(status, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

        if (!client.configured) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "未配置 SUPABASE_URL / SUPABASE_ANON_KEY",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            return@Card
        }

        if (session == null) {
            Spacer(Modifier.height(8.dp))
            TextField(state = email, label = text.email, useLabelAsPlaceholder = true)
            Spacer(Modifier.height(8.dp))
            TextField(state = password, label = text.password, useLabelAsPlaceholder = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = text.signIn,
                    onClick = {
                        run {
                            val next = client.signIn(email.text.toString().trim(), password.text.toString())
                            saveSession(prefs, next)
                            session = next
                            text.syncOn
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = text.signUp,
                    onClick = {
                        run {
                            val next = client.signUp(email.text.toString().trim(), password.text.toString())
                            if (next == null) {
                                "确认邮件已发送"
                            } else {
                                saveSession(prefs, next)
                                session = next
                                text.syncOn
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            return@Card
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "上传",
                onClick = {
                    val current = session ?: return@TextButton
                    run {
                        val revision = prefs.getLong("revision", 0L)
                        client.serverTime(current)?.let { Clock.setCorrection(it - Clock.now()) }
                        runCatching {
                            val row = client.save(current, controller.state, revision, controller.device)
                            prefs.edit().putLong("revision", row.revision).apply()
                            row.revision.toString()
                        }.getOrElse { error ->
                            if (error is CloudClient.ConflictException) {
                                client.fetch(current)?.let { remote ->
                                    controller.applyRemote(remote.state)
                                    prefs.edit().putLong("revision", remote.revision).apply()
                                }
                                text.syncOn
                            } else {
                                throw error
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "下载",
                onClick = {
                    val current = session ?: return@TextButton
                    run {
                        val remote = client.fetch(current)
                        if (remote == null) {
                            text.syncOn
                        } else {
                            controller.applyRemote(remote.state)
                            prefs.edit().putLong("revision", remote.revision).apply()
                            text.syncOn
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            text = text.signOut,
            onClick = {
                prefs.edit().clear().apply()
                session = null
                status = text.syncLocalOnly
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun loadSession(prefs: android.content.SharedPreferences): CloudClient.Session? {
    val raw = prefs.getString("session", null) ?: return null
    return runCatching {
        val o = JSONObject(raw)
        CloudClient.Session(
            userId = o.getString("userId"),
            accessToken = o.getString("access"),
            refreshToken = o.optString("refresh"),
        )
    }.getOrNull()
}

private fun saveSession(prefs: android.content.SharedPreferences, session: CloudClient.Session) {
    prefs.edit().putString(
        "session",
        JSONObject().apply {
            put("userId", session.userId)
            put("access", session.accessToken)
            put("refresh", session.refreshToken)
        }.toString(),
    ).apply()
}
