package com.pomodorotimer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorotimer.app.domain.*
import com.pomodorotimer.app.ui.i18n.LocalUiText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun StatsScreen(controller: WorkspaceController, now: Long, scrollBehavior: ScrollBehavior, padding: PaddingValues) {
    val state = controller.state
    val text = LocalUiText.current
    val en = state.settings.language == Language.EN
    var period by remember { mutableStateOf(StatisticsPeriod.WEEK) }
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var deletingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = remember(today, period, offset) { statisticsDays(today, period, offset) }
    val totals = remember(state.records) { dailyTotals(state.records) }
    val records = remember(state.records, days) { recordsInDays(state.records, days).sortedByDescending { it.start } }
    val hours = remember(state.records, days) { hourlyTotals(state.records, days) }
    val seconds = days.sumOf { totals[it.toString()] ?: 0L }
    val previous = remember(today, period, offset) { statisticsDays(today, period, offset - 1) }
    val previousSeconds = previous.sumOf { totals[it.toString()] ?: 0L }
    val goal = rangeGoal(state.settings, days, totals, state.records, now)
    var streakDay = if ((totals[today.toString()] ?: 0L) >= state.settings.streakMinutes.coerceAtLeast(1) * 60L) today else today.minusDays(1)
    var streak = 0
    while ((totals[streakDay.toString()] ?: 0L) >= state.settings.streakMinutes.coerceAtLeast(1) * 60L && streak < 36500) {
        streak++
        streakDay = streakDay.minusDays(1)
    }
    val visibleRecords = if (selected == null) records else recordsInDays(state.records, listOf(selected!!)).sortedByDescending { it.start }
    fun duration(value: Long): String = if (en) {
        when { value >= 3600 -> "${value / 3600}h ${(value % 3600) / 60}m"; value >= 60 -> "${value / 60}m"; else -> "${value}s" }
    } else formatDuration(value)
    val start = days.first()
    val end = days.last()
    fun dateLabel(date: LocalDate, showYear: Boolean): String = if (en) {
        date.format(DateTimeFormatter.ofPattern(if (showYear) "MMM d, yyyy" else "MMM d", java.util.Locale.ENGLISH))
    } else if (showYear) "${date.year}年${date.monthValue}月${date.dayOfMonth}日" else "${date.monthValue}月${date.dayOfMonth}日"
    val label = when (period) {
        StatisticsPeriod.DAY -> dateLabel(start, start.year != today.year)
        StatisticsPeriod.WEEK -> {
            val yearsDiffer = start.year != end.year
            val showYear = yearsDiffer || start.year != today.year
            if (en && start.year == end.year && start.month == end.month) {
                "${start.format(DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH))} – ${end.dayOfMonth}" + if (showYear) ", ${start.year}" else ""
            } else if (!en && start.year == end.year && start.month == end.month) {
                (if (showYear) "${start.year}年" else "") + "${start.monthValue}月${start.dayOfMonth}日 – ${end.dayOfMonth}日"
            } else dateLabel(start, showYear) + " – " + dateLabel(end, showYear)
        }
        StatisticsPeriod.MONTH -> if (en) start.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)) else "${start.year}年${start.monthValue}月"
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TabRowWithContour(
                    listOf(text.rangeDay, text.rangeWeek, text.rangeMonth), period.ordinal,
                    { index -> period = StatisticsPeriod.entries[index]; offset = 0; selected = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset--; selected = null }, modifier = Modifier.size(48.dp)) {
                        Icon(OutlineIcons.Previous, contentDescription = if (en) "Previous period" else "上一时段", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { offset++; selected = null }, enabled = offset < 0, modifier = Modifier.size(48.dp)) {
                        Icon(OutlineIcons.Next, contentDescription = if (en) "Next period" else "下一时段", tint = colorScheme.primary.copy(alpha = if (offset < 0) 1f else 0.3f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(text.totalFocus, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(6.dp))
                        Text(duration(seconds), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text((if (en) "Previous period · " else "上一时段 · ") + duration(previousSeconds), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(4.dp))
                        Text((if (en) "Current streak · $streak days" else "连续专注 · $streak 天"), fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(22.dp))
                        val chart = if (period == StatisticsPeriod.DAY) hours else days.map { totals[it.toString()] ?: 0L }
                        val max = maxOf(60L, chart.maxOrNull() ?: 0L)
                        Row(Modifier.fillMaxWidth().height(135.dp), horizontalArrangement = Arrangement.spacedBy(if (chart.size > 14) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
                            chart.forEachIndexed { index, value ->
                                val date = if (period == StatisticsPeriod.DAY) days.first() else days[index]
                                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${if (period == StatisticsPeriod.DAY) "$index:00" else date.toString()} ${duration(value)}" }.clickable { selected = if (selected == date) null else date }, contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().height((130f * value / max).coerceAtLeast(3f).dp).background(if (value > 0) colorScheme.primary.copy(alpha = if (selected == null || selected == date) 0.85f else 0.3f) else colorScheme.surfaceVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val labels = when (period) {
                                StatisticsPeriod.DAY -> listOf("0", "6", "12", "18", "24")
                                StatisticsPeriod.WEEK -> if (en) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("一", "二", "三", "四", "五", "六", "日")
                                StatisticsPeriod.MONTH -> listOf("1", "5", "10", "15", "20", "25", "${days.size}")
                            }
                            labels.forEach { Text(it, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) }
                        }
                        Spacer(Modifier.height(22.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatMetric(if (en) "Sessions" else "专注次数", "${records.size}")
                            StatMetric(if (en) "Goal" else "目标完成", goal?.let { "$it%" } ?: "—")
                            StatMetric(if (en) "Longest" else "最长一次", duration(records.maxOfOrNull { it.seconds } ?: 0L))
                        }
                    }
                }
            }
            if (period != StatisticsPeriod.DAY) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(if (en) "Time of day" else "时段分布", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(18.dp))
                        val hourMax = maxOf(60L, hours.maxOrNull() ?: 0L)
                        Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                            hours.forEachIndexed { hour, value ->
                                Box(Modifier.weight(1f).height((76f * value / hourMax).coerceAtLeast(3f).dp).semantics { contentDescription = "$hour:00 ${duration(value)}" }.background(if (value > 0) colorScheme.primary.copy(alpha = 0.8f) else colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("0", "6", "12", "18", "24").forEach { Text(it, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(if (en) "Focus calendar" else "专注日历", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        val month = days.first().withDayOfMonth(1)
                        Text(if (en) month.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)) else "${month.year}年${month.monthValue}月", fontSize = 13.sp, color = colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.height(14.dp))
                        val weekdays = if (en) listOf("M", "T", "W", "T", "F", "S", "S") else listOf("一", "二", "三", "四", "五", "六", "日")
                        Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary) } }
                        Spacer(Modifier.height(6.dp))
                        val blank = month.dayOfWeek.value - 1
                        val cells = blank + month.lengthOfMonth()
                        repeat((cells + 6) / 7) { week ->
                            Row(Modifier.fillMaxWidth()) {
                                repeat(7) { weekday ->
                                    val day = week * 7 + weekday - blank + 1
                                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                        if (day in 1..month.lengthOfMonth()) {
                                            val date = month.withDayOfMonth(day)
                                            val value = totals[date.toString()] ?: 0L
                                            val active = selected == date
                                            Box(Modifier.size(36.dp).background(if (active) colorScheme.primary else if (value > 0) colorScheme.primary.copy(alpha = 0.18f + 0.35f * (value.toFloat() / (state.settings.dailyGoal.coerceAtLeast(1) * 60)).coerceIn(0f, 1f)) else colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).semantics { contentDescription = "${date} ${duration(value)}" }.clickable { selected = if (active) null else date }, contentAlignment = Alignment.Center) {
                                                Text("$day", color = if (active) colorScheme.surface else colorScheme.onSurface, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(selected?.toString() ?: if (en) "Focus history" else "专注记录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (selected != null) TextButton(text = if (en) "Clear" else "清除筛选", onClick = { selected = null })
                        }
                        Spacer(Modifier.height(18.dp))
                        if (visibleRecords.isEmpty()) {
                            Text(if (en) "No focus records yet" else "暂无专注记录", modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, color = colorScheme.onSurfaceVariantSummary)
                        } else visibleRecords.forEachIndexed { index, record ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (record.mode == Mode.COUNTDOWN) OutlineIcons.Hourglass else OutlineIcons.Timer,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(if (record.mode == Mode.COUNTDOWN) text.filterCountdown else text.filterStopwatch, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Spacer(Modifier.height(5.dp))
                                    Text(Instant.ofEpochMilli(record.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
                                }
                                Text(duration(record.seconds), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                IconButton(onClick = { deletingRecord = record }, modifier = Modifier.size(44.dp)) {
                                    Icon(OutlineIcons.Delete, contentDescription = if (en) "Delete record" else "删除记录", tint = colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (index != visibleRecords.lastIndex) Box(Modifier.fillMaxWidth().height(0.5.dp).background(colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)))
                        }
                    }
                }
            }
        }
    }
    WindowDialog(
        show = deletingRecord != null,
        title = if (en) "Delete focus record?" else "删除这条专注记录？",
        onDismissRequest = { deletingRecord = null },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = if (en) "Delete" else "删除", onClick = {
                deletingRecord?.let { controller.deleteRecord(it.id) }
                deletingRecord = null
            }, modifier = Modifier.fillMaxWidth())
            TextButton(text = if (en) "Cancel" else "取消", onClick = { deletingRecord = null }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
    }
}
