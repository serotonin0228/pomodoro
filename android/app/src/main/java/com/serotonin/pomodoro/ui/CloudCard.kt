package com.serotonin.pomodoro.ui

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.serotonin.pomodoro.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Cloud sync card.
 *
 * Accounts live in the same Supabase deployment the React Native app uses, and the document is
 * written through `save_workspace`, whose revision check turns a concurrent edit into an explicit
 * conflict instead of a silent overwrite.
 */
@Composable
fun CloudCard(controller: WorkspaceController) {
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
        ArrowPreference(
            title = text.sectionCloud,
            summary = status,
            enabled = false,
            startAction = {
                Icon(
                    imageVector = MiuixIcons.CloudFill,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.ActionIconGap),
                    tint = colorScheme.onBackground,
                )
            },
        )

        if (!client.configured) {
            ArrowPreference(title = text.notConfigured, enabled = false)
            return@Card
        }

        if (session == null) {
            TextField(
                state = email,
                label = text.email,
                useLabelAsPlaceholder = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            TextField(
                state = password,
                label = text.password,
                useLabelAsPlaceholder = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
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
                                text.confirmEmail
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

        ArrowPreference(
            title = text.upload,
            onClick = {
                val current = session ?: return@ArrowPreference
                run {
                    client.serverTime(current)?.let { Clock.setCorrection(it - Clock.now()) }
                    runCatching {
                        val row = client.save(
                            current,
                            controller.state,
                            prefs.getLong("revision", 0L),
                            controller.device,
                        )
                        prefs.edit().putLong("revision", row.revision).apply()
                        text.syncOn
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
        )
        ArrowPreference(
            title = text.download,
            onClick = {
                val current = session ?: return@ArrowPreference
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
        )
        ArrowPreference(
            title = text.signOut,
            onClick = {
                prefs.edit().clear().apply()
                session = null
                status = text.syncLocalOnly
            },
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
