package com.pomodoro.focus

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

object FocusGuardApps {
  fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  fun essential(context: Context): Set<String> {
    val pm = context.packageManager
    val safe = mutableSetOf(context.packageName, "android", "com.android.systemui", "com.android.settings",
      "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller",
      "com.google.android.packageinstaller", "com.android.phone", "com.android.server.telecom", "com.android.emergency")
    val intents = listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Settings.ACTION_SETTINGS),
      Intent(Intent.ACTION_DIAL), Intent("android.intent.action.EMERGENCY_DIAL"))
    for (intent in intents) for (info in pm.queryIntentActivities(intent, 0)) safe.add(info.activityInfo.packageName)
    try { context.getSystemService(TelecomManager::class.java).defaultDialerPackage?.let { safe.add(it) } } catch (_: Exception) { }
    try { for (ime in context.getSystemService(InputMethodManager::class.java).enabledInputMethodList) safe.add(ime.packageName) } catch (_: Exception) { }
    return safe
  }
}
