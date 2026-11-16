package com.serotonin.pomodoro.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.focus.FocusGuardApps
import com.serotonin.pomodoro.focus.FocusGuardStore
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import com.serotonin.pomodoro.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * App limits card: opt-in switch, blocklist/allowlist mode, package picker and the system
 * accessibility shortcut — all as Miuix preferences so it matches the rest of the settings page.
 */
@Composable
fun FocusGuardCard(controller: WorkspaceController) {
    val context = LocalContext.current
    val text = LocalUiText.current
    val prefs = remember { FocusGuardStore.prefs(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "blacklist") ?: "blacklist") }
    var picking by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = text.appLimitsSwitch,
            summary = text.appLimitsHint,
            checked = enabled,
            onCheckedChange = { value ->
                enabled = value
                prefs.edit().putBoolean("enabled", value).apply()
            },
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Dimens.ActionIconGap),
                    tint = colorScheme.onBackground,
                )
            },
        )
        OverlayDropdownPreference(
            items = listOf(text.blacklist, text.whitelist),
            selectedIndex = if (mode == "blacklist") 0 else 1,
            title = text.sectionAppLimits,
            summary = text.pickApps,
            onSelectedIndexChange = { index ->
                mode = if (index == 0) "blacklist" else "whitelist"
                prefs.edit().putString("mode", mode).apply()
            },
        )
        ArrowPreference(
            title = text.pickApps,
            onClick = { picking = true },
        )
        ArrowPreference(
            title = text.openAccessibility,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }

    WindowDialog(
        show = picking,
        title = text.pickApps,
        onDismissRequest = { picking = false },
    ) {
        PackagePicker(
            context = context,
            mode = mode,
            onClose = { picking = false },
        )
    }
}

@Composable
private fun PackagePicker(context: Context, mode: String, onClose: () -> Unit) {
    val prefs = remember { FocusGuardStore.prefs(context) }
    val key = if (mode == "blacklist") "blocked" else "allowed"
    val apps = remember(mode) { launchableApps(context) }
    var selected by remember(mode) {
        mutableStateOf(prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet())
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(apps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.second)
                    Switch(
                        checked = app.first in selected,
                        onCheckedChange = { checked ->
                            val next = selected.toMutableSet()
                            if (checked) next.add(app.first) else next.remove(app.first)
                            selected = next
                            prefs.edit().putStringSet(key, next).apply()
                        },
                    )
                }
            }
        }
        TextButton(
            text = LocalUiText.current.done,
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun launchableApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val essential = FocusGuardApps.essential(context)
    return pm.queryIntentActivities(FocusGuardApps.launcherIntent(), 0)
        .asSequence()
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it !in essential && it != context.packageName }
        .mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pkg to pm.getApplicationLabel(info).toString()
            }.getOrNull()
        }
        .sortedBy { it.second.lowercase() }
        .toList()
}
