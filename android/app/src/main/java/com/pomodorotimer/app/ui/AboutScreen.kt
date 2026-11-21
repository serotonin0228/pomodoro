package com.pomodorotimer.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorotimer.app.BuildConfig
import com.pomodorotimer.app.R
import com.pomodorotimer.app.ui.i18n.LocalUiText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val text = LocalUiText.current
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = text.sectionAbout,
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(OutlineIcons.Previous, contentDescription = text.back)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(colorResource(R.color.ic_launcher_background)),
                            )
                            Text(text.appName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                item { SettingsHeading(text.aboutFeatures) }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = text.timer, summary = text.aboutTiming, startAction = { PreferenceIcon(OutlineIcons.Alarm) })
                        BasicComponent(title = text.stats, summary = text.aboutStatistics, startAction = { PreferenceIcon(OutlineIcons.Stats) })
                        BasicComponent(title = text.sectionCloud, summary = text.aboutSync, startAction = { PreferenceIcon(OutlineIcons.Cloud) })
                        BasicComponent(title = text.sectionMusic, summary = text.aboutMusic, startAction = { PreferenceIcon(OutlineIcons.Music) })
                        BasicComponent(title = text.sectionAppLimits, summary = text.aboutAppLimits, startAction = { PreferenceIcon(OutlineIcons.Shield) })
                        BasicComponent(title = text.aboutWidgetName, summary = text.aboutWidget, startAction = { PreferenceIcon(OutlineIcons.Grid) })
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = text.aboutSource,
                            summary = "GitHub · MIT",
                            onClick = { uriHandler.openUri("https://github.com/serotonin0228/pomodoro") },
                        )
                    }
                }
            }
        }
    }
}
