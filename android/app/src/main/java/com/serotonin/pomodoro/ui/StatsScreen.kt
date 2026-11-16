package com.serotonin.pomodoro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serotonin.pomodoro.data.CsvExport
import com.serotonin.pomodoro.domain.Mode
import com.serotonin.pomodoro.domain.clock
import com.serotonin.pomodoro.domain.dailyTotals
import com.serotonin.pomodoro.domain.dayKey
import com.serotonin.pomodoro.domain.rangeGoal
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import com.serotonin.pomodoro.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

private enum class StatsRange { DAY, WEEK, MONTH }

private enum class HistoryFilter { ALL, COUNTDOWN, STOPWATCH }

/**
 * Statistics page.
 *
 * The headline number sits in a tonal card with a large watermark icon (the pattern used by the
 * KernelSU kit's status card), and every list row is a Miuix preference so the page reads like a
 * system statistics screen.
 */
@Composable
fun StatsScreen(
    controller: WorkspaceController,
    now: Long,
    scrollBehavior: ScrollBehavior,
    padding: PaddingValues,
) {
    val state = controller.state
    val text = LocalUiText.current
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.DAY) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            CsvExport.writeTo(context, uri, state.records)
            controller.toast = text.exported
        }
    }

    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val days: List<LocalDate> = when (range) {
        StatsRange.DAY -> listOf(today)
        StatsRange.WEEK -> (6 downTo 0).map { today.minusDays(it.toLong()) }
        StatsRange.MONTH -> (29 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val totals = remember(state.records) { dailyTotals(state.records) }
    val rangeMinutes = days.sumOf {
        val key = dayKey(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        (totals[key] ?: 0L) / 60
    }
    val goal = rangeGoal(state.settings, days, totals, state.records, now)
    val live = state.records
        .filter { !it.deleted }
        .filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.COUNTDOWN -> it.mode == Mode.COUNTDOWN
                HistoryFilter.STOPWATCH -> it.mode == Mode.STOPWATCH
            }
        }
        .sortedByDescending { it.start }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.Gutter)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(text.rangeDay, text.rangeWeek, text.rangeMonth),
                    selectedIndex = range.ordinal,
                    title = text.rangeLabel,
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Weeks,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                    onSelectedIndexChange = { index ->
                        range = StatsRange.entries.getOrElse(index) { StatsRange.DAY }
                    },
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = colorScheme.surfaceVariant),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = MiuixIcons.Timer,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 8.dp)
                            .size(Dimens.WatermarkIcon),
                        tint = colorScheme.primary.copy(alpha = 0.12f),
                    )
                    Column(modifier = Modifier.padding(Dimens.CardInner)) {
                        Text(text.totalFocus, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = text.minutes(rangeMinutes),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = text.records(live.size),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        if (goal != null) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = (goal / 100f).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = text.goalProgress(goal),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(text.filterAll, text.filterCountdown, text.filterStopwatch),
                    selectedIndex = filter.ordinal,
                    title = text.recent,
                    onSelectedIndexChange = { index ->
                        filter = HistoryFilter.entries.getOrElse(index) { HistoryFilter.ALL }
                    },
                )
                ArrowPreference(
                    title = text.exportCsv,
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                            tint = colorScheme.onBackground,
                        )
                    },
                    onClick = { exporter.launch(CsvExport.fileName(now)) },
                    enabled = live.isNotEmpty(),
                )
            }
        }

        items(days) { day ->
            val key = day.toString()
            val seconds = totals[key] ?: 0L
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = key,
                    summary = null,
                    endActions = {
                        Text(
                            text = if (seconds > 0) text.minutes(seconds / 60) else "—",
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    },
                )
            }
        }

        items(live.take(50)) { record ->
            val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
            val moment = remember(record.start) {
                Instant.ofEpochMilli(record.start).atZone(ZoneId.systemDefault()).format(formatter)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = moment,
                    summary = if (record.mode == Mode.COUNTDOWN) text.filterCountdown else text.filterStopwatch,
                    endActions = { Text(clock(record.seconds)) },
                )
            }
        }
    }
}
