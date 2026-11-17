package com.pomodorotimer.app.ui

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pomodorotimer.app.focus.AppSelection
import com.pomodorotimer.app.focus.FocusGuardService
import com.pomodorotimer.app.focus.FocusGuardApps
import com.pomodorotimer.app.focus.FocusGuardStore
import com.pomodorotimer.app.ui.i18n.LocalUiText
import com.pomodorotimer.app.ui.theme.Dimens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = accessibilityEnabled(context)
                val pending = prefs.getBoolean("pendingAccessibility", false)
                if (pending || (enabled && !granted)) {
                    enabled = granted && (pending || enabled)
                    prefs.edit().putBoolean("enabled", enabled).remove("pendingAccessibility").apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = text.appLimitsSwitch,
                    startAction = { PreferenceIcon(OutlineIcons.Shield) },
            checked = enabled,
            onCheckedChange = { value ->
                if (!value) {
                    enabled = false
                    prefs.edit().putBoolean("enabled", false).remove("pendingAccessibility").apply()
                } else if (accessibilityEnabled(context)) {
                    enabled = true
                    prefs.edit().putBoolean("enabled", true).remove("pendingAccessibility").apply()
                } else {
                    prefs.edit().putBoolean("pendingAccessibility", true).apply()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        prefs.edit().remove("pendingAccessibility").apply()
                    }
                }
            },
        )
        OverlayDropdownPreference(
            items = listOf(text.blacklist, text.whitelist),
            selectedIndex = if (mode == "blacklist") 0 else 1,
            title = text.sectionAppLimits,
                    startAction = { PreferenceIcon(OutlineIcons.Blocklist) },
            onSelectedIndexChange = { index ->
                mode = if (index == 0) "blacklist" else "whitelist"
                prefs.edit().putString("mode", mode).apply()
            },
        )
        ArrowPreference(
            title = text.pickApps,
                    startAction = { PreferenceIcon(OutlineIcons.Grid) },
            onClick = { picking = true },
        )

    }

    WindowDialog(
        show = picking,
        title = text.pickApps,
        onDismissRequest = { picking = false },
    ) {
        if (picking) PackagePicker(
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
    val initialSelection = remember(mode) { prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet() }
    var selected by remember(mode) { mutableStateOf(initialSelection) }
    var query by remember(mode) { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<Pair<String, String>>(), mode) {
        value = withContext(Dispatchers.IO) { AppSelection.order(launchableApps(context), initialSelection) }
    }
    val visible = remember(apps, query) { AppSelection.filter(apps, query) }
    val visibleIds = remember(visible) { visible.map { it.first }.toSet() }
    val english = LocalUiText.current.done == "Done"
    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(key, next).apply()
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listMaxHeight = (screenHeight * 0.34f).coerceIn(120.dp, 300.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = query, onValueChange = { query = it },
            label = if (english) "Search apps" else "搜索应用", useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = if (english) "Select all" else "全选", onClick = { save(AppSelection.selectAll(selected, visibleIds)) }, modifier = Modifier.weight(1f), enabled = visibleIds.isNotEmpty())
            TextButton(text = if (english) "Invert" else "反选", onClick = { save(AppSelection.invert(selected, visibleIds)) }, modifier = Modifier.weight(1f), enabled = visibleIds.isNotEmpty())
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
        ) {
            items(visible, key = { it.first }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.second,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Switch(
                        checked = app.first in selected,
                        onCheckedChange = { checked ->
                            val next = selected.toMutableSet()
                            if (checked) next.add(app.first) else next.remove(app.first)
                            save(next)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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

private fun accessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, FocusGuardService::class.java)
    val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return services.split(':').any { ComponentName.unflattenFromString(it) == expected }
}
