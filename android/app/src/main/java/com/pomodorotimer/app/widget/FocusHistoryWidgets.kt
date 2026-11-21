package com.pomodorotimer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.pomodorotimer.app.MainActivity
import com.pomodorotimer.app.R
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Language
import com.pomodorotimer.app.domain.StatisticsPeriod
import com.pomodorotimer.app.domain.dailyTotals
import com.pomodorotimer.app.domain.focusCalendarIntensity
import com.pomodorotimer.app.domain.statisticsDays
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class FocusCalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        refreshHistoryWidgets(context, manager, calendarIds = ids)
}

class FocusStatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        refreshHistoryWidgets(context, manager, statsIds = ids)
}

internal fun refreshWorkspaceWidgets(context: Context) {
    runCatching { TimerWidget.refresh(context) }
    runCatching {
        val manager = AppWidgetManager.getInstance(context)
        refreshHistoryWidgets(
            context,
            manager,
            calendarIds = manager.getAppWidgetIds(ComponentName(context, FocusCalendarWidget::class.java)),
            statsIds = manager.getAppWidgetIds(ComponentName(context, FocusStatsWidget::class.java)),
        )
    }
}

private fun refreshHistoryWidgets(
    context: Context,
    manager: AppWidgetManager,
    calendarIds: IntArray = intArrayOf(),
    statsIds: IntArray = intArrayOf(),
) {
    if (calendarIds.isEmpty() && statsIds.isEmpty()) return

    val state = WorkspaceStore(context).loadActive()?.state
    val english = state?.settings?.language?.let { it == Language.EN }
        ?: (context.resources.configuration.locales[0].language != "zh")
    val locale = if (english) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
    val today = Instant.ofEpochMilli(Clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
    val totals = dailyTotals(state?.records.orEmpty())
    val accent = context.getColor(R.color.widget_accent)
    val surface = context.getColor(R.color.widget_surface)
    val quiet = context.getColor(R.color.widget_quiet_surface)
    val openStats = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_STATS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    fun duration(seconds: Long): String {
        if (seconds in 1..59) return if (english) "${seconds}s" else "${seconds} 秒"
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours == 0L) {
            if (english) "${minutes}m" else "${minutes} 分钟"
        } else {
            if (english) "${hours}h ${minutes % 60}m" else "${hours} 小时 ${minutes % 60} 分"
        }
    }
    fun weekday(day: DayOfWeek): String =
        if (english) day.getDisplayName(TextStyle.SHORT, locale)
        else "一二三四五六日"[day.ordinal].toString()

    if (calendarIds.isNotEmpty()) {
        val month = YearMonth.from(today)
        val first = month.atDay(1)
        val weekdayOffset = first.dayOfWeek.ordinal
        val weekCount = (weekdayOffset + month.lengthOfMonth() + 6) / 7
        val monthLabel = first.format(DateTimeFormatter.ofPattern(if (english) "MMMM yyyy" else "yyyy 年 M 月", locale))
        val view = RemoteViews(context.packageName, R.layout.focus_calendar_widget)
        view.setTextViewText(R.id.calendar_widget_month, monthLabel)
        view.setOnClickPendingIntent(R.id.widget_root, openStats)
        view.removeAllViews(R.id.calendar_widget_grid)

        val weekdayRow = RemoteViews(context.packageName, R.layout.widget_calendar_row)
        for (day in DayOfWeek.values()) {
            weekdayRow.addView(R.id.widget_calendar_row, calendarCell(context, weekday(day)))
        }
        view.addView(R.id.calendar_widget_grid, weekdayRow)

        val shades = mutableMapOf<Int, Bitmap>()
        repeat(weekCount) { week ->
            val row = RemoteViews(context.packageName, R.layout.widget_calendar_row)
            repeat(7) { column ->
                val day = week * 7 + column - weekdayOffset + 1
                val cell = if (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    val seconds = totals[date.toString()] ?: 0L
                    val shade = if (seconds > 0) ColorUtils.blendARGB(surface, accent, focusCalendarIntensity(seconds)) else quiet
                    calendarCell(context, day.toString()).apply {
                        val fill = shades.getOrPut(shade) { calendarShade(shade) }
                        setImageViewBitmap(R.id.widget_calendar_shade, fill)
                        val foreground = if (ColorUtils.calculateLuminance(shade) > 0.179) Color.BLACK else Color.WHITE
                        setTextColor(R.id.widget_calendar_label, foreground)
                        setContentDescription(R.id.widget_calendar_label, "$date, ${duration(seconds)}")
                    }
                } else {
                    calendarCell(context, "")
                }
                row.addView(R.id.widget_calendar_row, cell)
            }
            view.addView(R.id.calendar_widget_grid, row)
        }
        manager.updateAppWidget(calendarIds, view)
    }

    if (statsIds.isNotEmpty()) {
        val week = statisticsDays(today, StatisticsPeriod.WEEK, 0)
        val seconds = week.map { totals[it.toString()] ?: 0L }
        val view = RemoteViews(context.packageName, R.layout.focus_stats_widget)
        view.setOnClickPendingIntent(R.id.widget_root, openStats)
        view.setTextViewText(R.id.widget_stats_today_label, if (english) "Today" else "今日专注")
        view.setTextViewText(R.id.widget_stats_today, duration(totals[today.toString()] ?: 0L))
        view.setTextViewText(R.id.widget_stats_week_label, if (english) "This week" else "本周专注")
        view.setTextViewText(R.id.widget_stats_week, duration(seconds.sum()))
        view.setImageViewBitmap(R.id.widget_stats_chart, weekChart(seconds, accent, quiet))
        view.setContentDescription(
            R.id.widget_stats_chart,
            week.zip(seconds).joinToString(", ") { (date, value) -> "${weekday(date.dayOfWeek)} ${duration(value)}" },
        )
        view.removeAllViews(R.id.widget_stats_days)
        week.forEach { date ->
            view.addView(R.id.widget_stats_days, calendarCell(context, weekday(date.dayOfWeek)))
        }
        manager.updateAppWidget(statsIds, view)
    }
}

private fun calendarCell(context: Context, label: String): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_calendar_day).apply {
        setTextViewText(R.id.widget_calendar_label, label)
    }

private fun calendarShade(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    Canvas(bitmap).drawRoundRect(0f, 0f, 32f, 32f, 7f, 7f, paint)
    return bitmap
}

private fun weekChart(seconds: List<Long>, accent: Int, quiet: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(280, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val max = maxOf(seconds.max(), 60L)
    seconds.forEachIndexed { index, value ->
        val left = index * 40f + 9f
        val height = (64f * value / max).coerceAtLeast(3f)
        paint.color = if (value > 0) accent else quiet
        canvas.drawRoundRect(left, 64f - height, left + 22f, 64f, 6f, 6f, paint)
    }
    return bitmap
}
