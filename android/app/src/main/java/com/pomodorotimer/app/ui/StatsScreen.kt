package com.pomodorotimer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import kotlin.math.sqrt
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun StatsScreen(controller: WorkspaceController, now: Long, scrollBehavior: ScrollBehavior, padding: PaddingValues) {
    val state = controller.state
    val text = LocalUiText.current
    val en = state.settings.language == Language.EN
    var period by rememberSaveable { mutableStateOf(StatisticsPeriod.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedDate?.let(LocalDate::parse)
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var deletingRecord by remember { mutableStateOf<FocusRecord?>(null) }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    var customStart by rememberSaveable { mutableStateOf<String?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<String?>(null) }
    var editRange by remember { mutableStateOf(false) }
    var startInput by remember { mutableStateOf("") }
    var endInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf(false) }
    val custom = remember(customStart, customEnd, today) {
        val first = customStart ?: return@remember null
        val last = customEnd ?: return@remember null
        runCatching { customStatisticsDays(LocalDate.parse(first), LocalDate.parse(last), today) }.getOrNull()
    }
    val days = custom ?: remember(today, period, offset) { statisticsDays(today, period, offset) }
    val byHour = custom == null && period == StatisticsPeriod.DAY
    val totals = remember(state.records) { dailyTotals(state.records) }
    val records = remember(state.records, days) { recordsInDays(state.records, days).sortedByDescending { it.start } }
    val hours = remember(state.records, days) { hourlyTotals(state.records, days) }
    val seconds = days.sumOf { totals[it.toString()] ?: 0L }
    val previous = remember(today, period, offset, custom) {
        if (custom == null) statisticsDays(today, period, offset - 1)
        else custom.map { it.minusDays(custom.size.toLong()) }
    }
    val previousSeconds = previous.sumOf { totals[it.toString()] ?: 0L }
    val goal = rangeGoal(state.settings, days, totals, state.records, now)
    var streakDay = if ((totals[today.toString()] ?: 0L) >= state.settings.streakMinutes.coerceAtLeast(1) * 60L) today else today.minusDays(1)
    var streak = 0
    while ((totals[streakDay.toString()] ?: 0L) >= state.settings.streakMinutes.coerceAtLeast(1) * 60L && streak < 36500) {
        streak++
        streakDay = streakDay.minusDays(1)
    }
    val selectedRecords = remember(state.records, selected, records) {
        if (selected == null) records else recordsInDays(state.records, listOf(selected)).sortedByDescending { it.start }
    }
    val visibleRecords = remember(selectedRecords, filterIndex) {
        if (filterIndex == 0) selectedRecords
        else selectedRecords.filter { it.mode == Mode.entries[filterIndex - 1] }
    }
    fun duration(value: Long): String = if (en) {
        when { value >= 3600 -> "${value / 3600}h ${(value % 3600) / 60}m"; value >= 60 -> "${value / 60}m"; else -> "${value}s" }
    } else formatDuration(value)
    val start = days.first()
    val end = days.last()
    fun dateLabel(date: LocalDate, showYear: Boolean): String = if (en) {
        date.format(DateTimeFormatter.ofPattern(if (showYear) "MMM d, yyyy" else "MMM d", java.util.Locale.ENGLISH))
    } else if (showYear) "${date.year}年${date.monthValue}月${date.dayOfMonth}日" else "${date.monthValue}月${date.dayOfMonth}日"
    val label = if (custom != null) dateLabel(start, start.year != today.year || start.year != end.year) +
        " – " + dateLabel(end, end.year != today.year || start.year != end.year)
    else when (period) {
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
                    listOf(text.rangeDay, text.rangeWeek, text.rangeMonth, text.rangeCustom),
                    if (custom != null) 3 else period.ordinal,
                    { index ->
                        if (index == 3) {
                            startInput = start.toString()
                            endInput = minOf(end, today).toString()
                            rangeError = false
                            editRange = true
                        } else {
                            customStart = null
                            customEnd = null
                            period = StatisticsPeriod.entries[index]
                            offset = 0
                            selectedDate = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    indication = null,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset--; selectedDate = null }, enabled = custom == null, modifier = Modifier.size(48.dp)) {
                        Icon(OutlineIcons.Previous, contentDescription = if (en) "Previous period" else "上一时段", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { offset++; selectedDate = null }, enabled = custom == null && offset < 0, modifier = Modifier.size(48.dp)) {
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
                        val buckets = days.chunked((days.size + 30) / 31)
                        val chart = if (byHour) hours else buckets.map { bucket -> bucket.sumOf { totals[it.toString()] ?: 0L } }
                        val max = maxOf(60L, chart.maxOrNull() ?: 0L)
                        Row(Modifier.fillMaxWidth().height(135.dp), horizontalArrangement = Arrangement.spacedBy(if (chart.size > 14) 2.dp else 8.dp), verticalAlignment = Alignment.Bottom) {
                            chart.forEachIndexed { index, value ->
                                val bucket = if (byHour) days else buckets[index]
                                val date = bucket.first()
                                val chartLabel = if (byHour) "$index:00" else if (bucket.size == 1) date.toString() else "$date – ${bucket.last()}"
                                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "$chartLabel ${duration(value)}" }
                                    .clickable(enabled = bucket.size == 1) { selectedDate = if (selected == date) null else date.toString() },
                                    contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().height((130f * value / max).coerceAtLeast(3f).dp).background(if (value > 0) colorScheme.primary.copy(alpha = if (selected == null || selected == date) 0.85f else 0.3f) else colorScheme.surfaceVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val labels = if (custom != null) listOf(dateLabel(start, false), dateLabel(end, false))
                            else when (period) {
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
            if (!byHour) item {
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
                                            val shape = RoundedCornerShape(10.dp)
                                            val background = if (value == 0L) colorScheme.surfaceVariant else {
                                                val fraction = (value / (4 * 3600f)).coerceAtMost(1f)
                                                val intensity = 0.11f + 0.44f * sqrt(fraction)
                                                colorScheme.primary.copy(alpha = intensity).compositeOver(colorScheme.surface)
                                            }
                                            val foreground = when {
                                                value == 0L -> colorScheme.onSurface
                                                background.luminance() > 0.179f -> Color.Black
                                                else -> Color.White
                                            }
                                            val selection = if (active) Modifier.border(2.dp, foreground, shape) else Modifier
                                            Box(
                                                modifier = Modifier.size(36.dp).clip(shape)
                                                    .background(background)
                                                    .then(selection)
                                                    .semantics {
                                                        contentDescription = "$date ${duration(value)}"
                                                        this.selected = active
                                                    }
                                                    .clickable { selectedDate = if (active) null else date.toString() },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("$day", color = foreground, fontSize = 13.sp)
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
                            if (selected != null) TextButton(text = if (en) "Clear" else "清除筛选", onClick = { selectedDate = null })
                        }
                        if (selected != null) {
                            Text(duration(totals[selected.toString()] ?: 0L) + " · " +
                                if (en) "${selectedRecords.size} sessions" else "${selectedRecords.size} 次专注",
                                color = colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (selectedRecords.isNotEmpty() || filterIndex != 0) {
                            TabRowWithContour(
                                listOf(if (en) "All" else "全部", text.filterCountdown, text.filterStopwatch),
                                filterIndex,
                                { filterIndex = it },
                                modifier = Modifier.fillMaxWidth(),
                                indication = null,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
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
        show = editRange,
        title = if (en) "Date range" else "选择日期范围",
        summary = if (rangeError) {
            if (en) "Use a valid range of up to 366 days, ending by today." else "请输入有效日期，最多 366 天，结束日期不能晚于今天。"
        } else "YYYY-MM-DD",
        onDismissRequest = { editRange = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = startInput, onValueChange = { startInput = it.take(10) },
                label = if (en) "Start date" else "开始日期", singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = endInput, onValueChange = { endInput = it.take(10) },
                label = if (en) "End date" else "结束日期", singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(text = if (en) "Apply" else "应用", modifier = Modifier.fillMaxWidth(), onClick = {
                val range = runCatching {
                    customStatisticsDays(LocalDate.parse(startInput.trim()), LocalDate.parse(endInput.trim()), today)
                }.getOrNull()
                if (range == null) rangeError = true
                else {
                    customStart = range.first().toString()
                    customEnd = range.last().toString()
                    selectedDate = null
                    editRange = false
                }
            })
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
