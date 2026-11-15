package com.serotonin.pomodoro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.serotonin.pomodoro.data.CsvExport
import com.serotonin.pomodoro.domain.FocusRecord
import com.serotonin.pomodoro.domain.clock
import com.serotonin.pomodoro.domain.dailyTotals
import com.serotonin.pomodoro.domain.dayKey
import com.serotonin.pomodoro.domain.rangeGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.serotonin.pomodoro.ui.i18n.LocalUiText
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class StatsRange { DAY, WEEK, MONTH }

private enum class HistoryFilter { ALL, COUNTDOWN, STOPWATCH }

/**
 * Statistics: range totals, goal progress, per-day breakdown, recent sessions and CSV export.
 *
 * The goal maths is the same function the domain tests cover, so the number shown here and the
 * number produced by `rangeGoal` in the test suite can never drift apart.
 */
@Composable
fun StatsScreen(
    controller: WorkspaceController,
    now: Long,
) {
    val text = LocalUiText.current
    val state = controller.state
    val context = LocalContext.current
    var range by remember { mutableStateOf(StatsRange.DAY) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            CsvExport.writeTo(context, uri, state.records)
            controller.toast = "已导出 CSV"
        }
    }

    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val days: List<LocalDate> = when (range) {
        StatsRange.DAY -> listOf(today)
        StatsRange.WEEK -> (6 downTo 0).map { today.minusDays(it.toLong()) }
        StatsRange.MONTH -> (29 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val totals = remember(state.records, range) { dailyTotals(state.records) }
    val rangeMinutes = days.sumOf { (totals[dayKey(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())] ?: 0L) } / 60
    val goal = rangeGoal(state.settings, days, totals, state.records, now)
    val live = state.records
        .filter { !it.deleted }
        .filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.COUNTDOWN -> it.mode == com.serotonin.pomodoro.domain.Mode.COUNTDOWN
                HistoryFilter.STOPWATCH -> it.mode == com.serotonin.pomodoro.domain.Mode.STOPWATCH
            }
        }
        .sortedByDescending { it.start }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { option ->
                    val label = when (option) {
                        StatsRange.DAY -> text.byDay
                        StatsRange.WEEK -> text.minutes(7)
                        StatsRange.MONTH -> text.minutes(30)
                    }
                    if (option == range) {
                        Button(onClick = { range = option }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    } else {
                        TextButton(
                            text = label,
                            onClick = { range = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(text.totalFocus)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text.minutes(rangeMinutes),
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text.records(live.size),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (goal != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(text.goalProgress(goal))
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (goal / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            TextButton(
                text = text.exportCsv,
                onClick = { exporter.launch(CsvExport.fileName(now)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = live.isNotEmpty(),
            )
        }

        item {
            Text(
                text = text.byDay,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        items(days) { day ->
            val key = day.toString()
            val seconds = totals[key] ?: 0L
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(key)
                    Text(if (seconds > 0) text.minutes(seconds / 60) else "—")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { option ->
                    TextButton(
                        text = when (option) {
                            HistoryFilter.ALL -> text.filterAll
                            HistoryFilter.COUNTDOWN -> text.filterCountdown
                            HistoryFilter.STOPWATCH -> text.filterStopwatch
                        },
                        onClick = { filter = option },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Text(
                text = text.recent,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        items(live.take(50)) { record ->
            RecordRow(record)
        }
    }
}

@Composable
private fun RecordRow(record: FocusRecord) {
    val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    val moment = remember(record.start) {
        Instant.ofEpochMilli(record.start).atZone(ZoneId.systemDefault()).format(formatter)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(moment)
            Text(clock(record.seconds))
        }
    }
}
