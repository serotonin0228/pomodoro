package com.pomodoro.focus

object FocusGuardPolicy {
  fun sameBoot(boot: Int, sessionBoot: Int, epoch: Long, sessionEpoch: Long, uptime: Long, sessionUptime: Long): Boolean {
    if (boot >= 0 && sessionBoot >= 0) return boot == sessionBoot
    // Some vendors do not expose BOOT_COUNT. A saved uptime and boot epoch provide a local fallback.
    return sessionEpoch > 0 && uptime >= sessionUptime && kotlin.math.abs(epoch - sessionEpoch) <= 120000L
  }
  fun isActive(enabled: Boolean, status: String, phase: String, sameBoot: Boolean,
               countdown: Boolean, deadline: Long, now: Long): Boolean =
    enabled && status == "running" && phase == "focus" && sameBoot && (!countdown || now < deadline)

  fun shouldBlock(packageName: String, mode: String, blocked: Set<String>, allowed: Set<String>,
                  launchable: Set<String>, essential: Set<String>): Boolean {
    if (packageName.isBlank() || packageName in essential) return false
    return when (mode) {
      // Explicitly selected packages must not depend on a second launcher query/cache.
      "blacklist" -> packageName in blocked
      "whitelist" -> packageName in launchable && packageName !in allowed
      else -> false
    }
  }
}
