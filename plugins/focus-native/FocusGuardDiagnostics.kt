package com.pomodoro.focus

/** Ephemeral local diagnostics; never sent to a server or persisted as usage history. */
object FocusGuardDiagnostics {
  @Volatile var events = 0
  @Volatile var lastSeen = ""
  @Volatile var lastMatched = ""
  @Volatile var lastError = ""
  @Volatile var lastHome = false
  @Volatile var returns = 0
}
