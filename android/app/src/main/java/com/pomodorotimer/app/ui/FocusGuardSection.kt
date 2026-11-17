package com.pomodorotimer.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.pomodorotimer.app.focus.FocusGuardApps
import com.pomodorotimer.app.focus.FocusGuardStore
import com.pomodorotimer.app.ui.i18n.LocalUiText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * "App limits" section: opt-in switch, blacklist/whitelist mode and the package picker.
 *
 * The list is built from launcher activities only, exactly like the accessibility service does, and
 * the guard itself never reads screen content — it only compares package names.
 */
@Composable
fun FocusGuardSection() {
    val text = LocalUiText.current
    val context = LocalContext.current
    val prefs = remember { FocusGuardStore.prefs(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "blacklist") ?: "blacklist") }
    var picking by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text.sectionAppLimits, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.appLimitsSwitch)
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("enabled", it).apply()
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = text.blacklist,
                onClick = {
                    mode = "blacklist"
                    prefs.edit().putString("mode", mode).apply()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = text.whitelist,
                onClick = {
                    mode = "whitelist"
                    prefs.edit().putString("mode", mode).apply()
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            text = "选择应用（当前：$mode）",
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            text = text.openAccessibility,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!enabled) {
            Text(
                text = text.appLimitsHint,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }

    OverlayDialog(
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
    var selected by remember(mode) { mutableStateOf(prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            items(apps) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(8.dp))
        TextButton(text = LocalUiText.current.done, onClick = onClose, modifier = Modifier.fillMaxWidth())
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
                val info: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                pkg to pm.getApplicationLabel(info).toString()
            }.getOrNull()
        }
        .sortedBy { it.second.lowercase() }
        .toList()
}
