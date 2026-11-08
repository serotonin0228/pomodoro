package com.pomodoro.focus

import android.content.Intent
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager

class FocusModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
  override fun getName() = "FocusStatus"
  @ReactMethod fun update(timer: ReadableMap, promise: Promise) {
    try {
      val status = timer.getString("status") ?: "idle"
      if (status != "running" && status != "paused") { ctx.stopService(Intent(ctx, FocusService::class.java)); ctx.getSystemService(android.app.NotificationManager::class.java).cancel(2408); promise.resolve(null); return }
      val intent = Intent(ctx, FocusService::class.java).putExtra("status",status).putExtra("language", if(timer.hasKey("language")) timer.getString("language") else "zh").putExtra("phase", timer.getString("phase")).putExtra("id",timer.getString("id")).putExtra("elapsed",(timer.getDouble("elapsed") * 1000).toLong()).putExtra("started", if (timer.isNull("startedAt")) System.currentTimeMillis() else timer.getDouble("startedAt").toLong()).putExtra("duration",(timer.getDouble("duration") * 1000).toLong()).putExtra("countdown",timer.getString("mode") == "countdown").putExtra("sound", if(timer.hasKey("sound")) timer.getBoolean("sound") else true)
      if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
      promise.resolve(null)
    } catch(e: Exception) { promise.reject("FOCUS_NOTIFICATION", e) }
  }
  @ReactMethod fun openLiveSettings() {
    val live = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra("android.provider.extra.APP_PACKAGE", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(live) } catch(e: Exception) { ctx.startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }
  @ReactMethod fun drain(promise: Promise) { synchronized(FocusService::class.java) { val prefs = ctx.getSharedPreferences("focus-actions",0); val actions = prefs.getString("queue","[]"); prefs.edit().remove("queue").commit(); promise.resolve(actions) } }
}
class FocusPackage : ReactPackage {
  override fun createNativeModules(ctx: ReactApplicationContext): List<NativeModule> = listOf(FocusModule(ctx))
  override fun createViewManagers(ctx: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}
