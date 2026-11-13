package com.pomodoro.focus

fun main() {
  fun active(enabled: Boolean = true, status: String = "running", phase: String = "focus", sameBoot: Boolean = true, countdown: Boolean = true, deadline: Long = 5000, now: Long = 1000) =
    FocusGuardPolicy.isActive(enabled, status, phase, sameBoot, countdown, deadline, now)
  check(active())
  check(!active(enabled = false))
  for (status in listOf("idle", "paused", "completed")) check(!active(status = status))
  check(!active(phase = "break"))
  check(active(now = 4999)); check(!active(now = 5000)); check(!active(now = 9999))
  check(active(countdown = false, now = 99999999))
  check(!active(sameBoot = false))
  check(FocusGuardPolicy.sameBoot(9, 9, 0, 0, 9000, 1000))
  check(!FocusGuardPolicy.sameBoot(10, 9, 0, 0, 9000, 1000))
  check(FocusGuardPolicy.sameBoot(-1, -1, 1000000, 1000000, 9000, 1000))
  check(!FocusGuardPolicy.sameBoot(-1, -1, 1000000, 1000000, 500, 1000))
  check(!FocusGuardPolicy.sameBoot(-1, -1, 2000000, 1000000, 9000, 1000))
  check(!FocusGuardPolicy.sameBoot(-1, -1, 1000000, 0, 9000, 1000))
  val apps = setOf("video", "reader", "phone", "settings", "launcher", "timer", "keyboard")
  val essential = setOf("phone", "settings", "launcher", "timer", "keyboard")
  fun block(pkg: String, mode: String = "blacklist", blocked: Set<String> = setOf("video"), allowed: Set<String> = setOf("reader")) =
    FocusGuardPolicy.shouldBlock(pkg, mode, blocked, allowed, apps, essential)
  check(FocusGuardPolicy.shouldBlock("video", "blacklist", setOf("video"), emptySet(), emptySet(), essential))
  check(block("video")); check(!block("reader")); check(!block("video", blocked = emptySet()))
  check(block("video", "whitelist")); check(!block("reader", "whitelist"))
  for (pkg in essential) {
    check(!block(pkg, blocked = apps))
    check(!block(pkg, "whitelist", allowed = emptySet()))
  }
  check(!block("permission-dialog", "whitelist", allowed = emptySet()))
  check(!block("", "whitelist")); check(!block("video", "unknown"))
  println("PASS: blacklist, whitelist, essentials, pause/end/break, deadline, stopwatch, reboot")
}
