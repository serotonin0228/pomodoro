package com.pomodoro.focus

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

class FocusService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private var status = "idle"
  private var elapsed = 0L
  private var started = 0L
  private var duration = 0L
  private var countdown = true
  private var timerId = ""
  private var sound = true
  private var language = "zh"
  private var phase = "focus"
  private fun label(chinese: String, english: String) = if (language == "en") english else chinese
  private val ticker = object : Runnable { override fun run() { if (status == "running" && countdown && current() >= duration) { status = "completed"; show(); stopForeground(STOP_FOREGROUND_DETACH); stopSelf() } else { if (status == "running") show(); handler.postDelayed(this, 1000) } } }
  override fun onBind(intent: Intent?) = null
  private fun current() = elapsed + if (status == "running") (System.currentTimeMillis() - started).coerceAtLeast(0) else 0L
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) { stopSelf(); return START_NOT_STICKY }
    when (intent.action) {
      "stop" -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
      "toggle" -> { val now = System.currentTimeMillis(); if (status == "running") { elapsed = current(); status = "paused" } else { started = now; status = "running" }; val prefs = getSharedPreferences("focus-actions", MODE_PRIVATE); synchronized(FocusService::class.java) { val queue = JSONArray(prefs.getString("queue", "[]")); queue.put(JSONObject().put("status", status).put("at", now).put("id", timerId)); prefs.edit().putString("queue", queue.toString()).commit() } }
      else -> { status = intent.getStringExtra("status") ?: "idle"; timerId = intent.getStringExtra("id") ?: ""; elapsed = intent.getLongExtra("elapsed", 0); started = intent.getLongExtra("started", System.currentTimeMillis()); duration = intent.getLongExtra("duration", 0); countdown = intent.getBooleanExtra("countdown", true); sound = intent.getBooleanExtra("sound", true); language = intent.getStringExtra("language") ?: "zh"; phase = intent.getStringExtra("phase") ?: "focus" }
    }
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) { manager.createNotificationChannel(NotificationChannel("focus-live", label("专注状态", "Focus status"), NotificationManager.IMPORTANCE_LOW)); manager.createNotificationChannel(NotificationChannel("timer", label("计时提醒", "Timer alerts"), NotificationManager.IMPORTANCE_HIGH)) }
    show(); handler.removeCallbacks(ticker); handler.postDelayed(ticker, 1000)
    return START_NOT_STICKY
  }
  private fun show() {
    val value = if (countdown) (duration - current()).coerceAtLeast(0) else current()
    val text = "%02d:%02d".format(value / 60000, value / 1000 % 60)
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val action = PendingIntent.getService(this, 1, Intent(this, FocusService::class.java).setAction("toggle"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(this, if (status == "completed" && sound) "timer" else "focus-live").setSmallIcon(R.drawable.focus_status).setContentTitle(if (phase == "break") { if (status == "paused") label("休息已暂停", "Break paused") else if (status == "completed") label("休息结束", "Break complete") else label("正在休息", "On a break") } else { if (status == "paused") label("专注已暂停", "Focus paused") else if(status == "completed") label("专注完成", "Focus complete") else label("正在专注", "Focusing") }).setContentText(text).setContentIntent(open).setOngoing(status != "completed").setOnlyAlertOnce(status != "completed").setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_PROGRESS).setRequestPromotedOngoing(status != "completed").setSilent(status != "completed" || !sound).setAutoCancel(false)
    if (status == "paused") notification.setShortCriticalText(text)
    if (status != "completed") notification.addAction(0, if (status == "paused") label("继续", "Resume") else label("暂停", "Pause"), action)
    if (status == "running") { notification.setUsesChronometer(true).setChronometerCountDown(countdown).setWhen(if (countdown) System.currentTimeMillis() + value else System.currentTimeMillis() - value) }
    startForeground(2408, notification.build())
  }
  override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
