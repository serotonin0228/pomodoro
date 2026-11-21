package com.pomodorotimer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.pomodorotimer.app.MainActivity
import com.pomodorotimer.app.R
import com.pomodorotimer.app.data.WorkspaceStore
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.Language
import com.pomodorotimer.app.domain.Mode
import com.pomodorotimer.app.domain.TimerStatus
import com.pomodorotimer.app.domain.clock
import com.pomodorotimer.app.domain.countdownDeadline
import com.pomodorotimer.app.domain.displaySeconds
import com.pomodorotimer.app.domain.isForeignActive
import com.pomodorotimer.app.domain.initialState
import com.pomodorotimer.app.service.TimerActionReceiver
import com.pomodorotimer.app.ui.WorkspaceController
import com.pomodorotimer.app.ui.i18n.uiText

class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PRIMARY) {
            super.onReceive(context, intent)
            return
        }
        val store = WorkspaceStore(context)
        val owner = store.activeAccount()
        val persisted = store.load(owner)?.state?.timer
        if (intent.getStringExtra(EXTRA_ACCOUNT) != owner || intent.getStringExtra(EXTRA_ID) != persisted?.id ||
            intent.getStringExtra(EXTRA_STATUS) != persisted?.status?.name
        ) {
            refresh(context)
            return
        }
        val controller = WorkspaceController(context)
        if (!controller.isRemoteActive) {
            controller.primary()
            context.sendBroadcast(Intent(TimerActionReceiver.ACTION_STATE_CHANGED).setPackage(context.packageName))
        }
        refresh(context)
    }

    companion object {
        private const val ACTION_PRIMARY = "com.pomodorotimer.app.widget.PRIMARY"
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_ID = "timerId"
        private const val EXTRA_STATUS = "timerStatus"

        internal fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, TimerWidget::class.java)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isEmpty()) return
            val store = WorkspaceStore(context)
            val account = store.activeAccount()
            val persisted = store.load(account)?.state
            val state = persisted ?: initialState(store.deviceId())
            val timer = state.timer
            val language = state.settings.language
            val text = uiText(language)
            val foreign = isForeignActive(timer, store.deviceId())
            val now = Clock.now()
            val running = timer.status == TimerStatus.RUNNING && !foreign
            val countdown = timer.mode == Mode.COUNTDOWN
            val uptime = SystemClock.elapsedRealtime()
            val deadline = countdownDeadline(timer, 0L)
            val chronometer = running && timer.startedAt != null && (!countdown || (deadline ?: 0L) > now)
            val base = if (countdown) uptime + ((deadline ?: now) - now).coerceAtLeast(0L)
                else uptime - timer.elapsed * 1000 - (now - (timer.startedAt ?: now)).coerceAtLeast(0L)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
            val tap = Intent(context, TimerWidget::class.java)
                .setAction(ACTION_PRIMARY)
                .setData(Uri.fromParts("pomodoro-widget", "${account.orEmpty()}/${persisted?.timer?.id.orEmpty()}/${persisted?.timer?.status}", null))
                .putExtra(EXTRA_ACCOUNT, account)
                .putExtra(EXTRA_ID, persisted?.timer?.id)
                .putExtra(EXTRA_STATUS, persisted?.timer?.status?.name)
            val primary = PendingIntent.getBroadcast(context, 1, tap, flags)

            val view = RemoteViews(context.packageName, R.layout.timer_widget)
            view.setTextViewText(R.id.widget_title, if (language == Language.ZH) "番茄钟" else "Pomodoro")
            view.setTextViewText(R.id.widget_time, clock(displaySeconds(timer, now)))
            view.setViewVisibility(R.id.widget_time, if (chronometer) View.GONE else View.VISIBLE)
            view.setViewVisibility(R.id.widget_chronometer, if (chronometer) View.VISIBLE else View.GONE)
            view.setChronometerCountDown(R.id.widget_chronometer, countdown)
            view.setChronometer(R.id.widget_chronometer, base, null, chronometer)
            val action = if (foreign) {
                if (language == Language.ZH) "打开" else "Open"
            } else when (timer.status) {
                TimerStatus.RUNNING -> text.pause
                TimerStatus.PAUSED -> text.resume
                TimerStatus.COMPLETED -> if (language == Language.ZH) "下一阶段" else "Next phase"
                TimerStatus.IDLE -> text.start
            }
            view.setTextViewText(R.id.widget_action, action)
            view.setOnClickPendingIntent(R.id.widget_root, open)
            view.setOnClickPendingIntent(R.id.widget_action, if (foreign) open else primary)
            manager.updateAppWidget(ids, view)
        }
    }
}
