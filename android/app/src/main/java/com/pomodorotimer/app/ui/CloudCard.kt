package com.pomodorotimer.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.data.CloudClient
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Download
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
    var showAccount by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val accountTitle = if (controller.state.settings.language == com.pomodorotimer.app.domain.Language.EN) "Account" else "账号"
    val en = controller.state.settings.language == com.pomodorotimer.app.domain.Language.EN
    val syncLabel = when {
        session == null || !client.configured -> if (en) "Not synced" else "未同步"
        controller.cloudSyncStatus == CloudSyncStatus.SYNCED -> if (en) "Synced" else "已同步"
        controller.cloudSyncStatus == CloudSyncStatus.SYNCING -> if (en) "Syncing" else "正在同步"
        else -> if (en) "Not synced" else "未同步"
    }
    val summary = syncLabel

    fun authenticate(register: Boolean) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try {
                val next = if (register) client.signUp(email.trim(), password) else client.signIn(email.trim(), password)
                if (next == null) {
                    message = text.confirmEmail
                } else {
                    saveSession(prefs, next)
                    session = next
                    password = ""
                    showAccount = false
                    controller.syncCloud()
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                message = text.syncError
            } finally {
                busy = false
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = accountTitle,
            summary = summary,
            startAction = { PreferenceIcon(OutlineIcons.Cloud) },
            onClick = { showAccount = true; message = "" },
        )
    }

    WindowDialog(show = showAccount, title = accountTitle, onDismissRequest = { if (!busy) showAccount = false }) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!client.configured) {
                Text(text.notConfigured)
            } else if (session == null) {
                TextField(
                    value = email, onValueChange = { email = it }, label = text.email,
                    useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true,
                )
                TextField(
                    value = password, onValueChange = { password = it }, label = text.password,
                    useLabelAsPlaceholder = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                )
                if (message.isNotBlank()) Text(message, color = colorScheme.onSurfaceVariantSummary)
                TextButton(
                    text = text.signIn, onClick = { authenticate(false) },
                    enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = text.signUp, onClick = { authenticate(true) },
                    enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(accountEmail(session!!.accessToken) ?: text.syncOn)
                if (controller.cloudSyncError) Text(text.syncError, color = colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(4.dp))
                TextButton(text = text.signOut, modifier = Modifier.fillMaxWidth(), onClick = {
                    prefs.edit().clear().apply()
                    session = null
                    password = ""
                    showAccount = false
                })
            }
        }
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

private fun accountEmail(token: String): String? = runCatching {
    val payload = token.split(".").getOrNull(1) ?: return@runCatching null
    val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    JSONObject(String(decoded, Charsets.UTF_8)).optString("email").takeIf { it.isNotBlank() }
}.getOrNull()
