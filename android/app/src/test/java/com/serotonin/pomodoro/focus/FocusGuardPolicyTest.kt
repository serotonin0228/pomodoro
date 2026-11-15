package com.serotonin.pomodoro.focus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of `tests/native/FocusGuardPolicyTest.kt` from the reference app.
 *
 * The rules are pure functions, so they are guarded here rather than through the accessibility
 * service: active-only-during-focus, deadline handling, reboot detection, whitelist/blacklist and
 * the always-allowed essentials.
 */
class FocusGuardPolicyTest {

    private fun active(
        enabled: Boolean = true,
        status: String = "running",
        phase: String = "focus",
        sameBoot: Boolean = true,
        countdown: Boolean = true,
        deadline: Long = 5000,
        now: Long = 1000,
    ) = FocusGuardPolicy.isActive(enabled, status, phase, sameBoot, countdown, deadline, now)

    @Test
    fun `guard is active only while a focus countdown is running on the same boot`() {
        assertTrue(active())
        assertFalse(active(enabled = false))
        listOf("idle", "paused", "completed").forEach { assertFalse(active(status = it)) }
        assertFalse(active(phase = "break"))
        assertTrue(active(now = 4999))
        assertFalse(active(now = 5000))
        assertFalse(active(now = 9999))
        assertTrue(active(countdown = false, now = 99_999_999))
        assertFalse(active(sameBoot = false))
    }

    @Test
    fun `reboot detection prefers boot count and falls back to epoch and uptime`() {
        assertTrue(FocusGuardPolicy.sameBoot(9, 9, 0, 0, 9000, 1000))
        assertFalse(FocusGuardPolicy.sameBoot(10, 9, 0, 0, 9000, 1000))
        assertTrue(FocusGuardPolicy.sameBoot(-1, -1, 1_000_000, 1_000_000, 9000, 1000))
        assertFalse(FocusGuardPolicy.sameBoot(-1, -1, 1_000_000, 1_000_000, 500, 1000))
        assertFalse(FocusGuardPolicy.sameBoot(-1, -1, 2_000_000, 1_000_000, 9000, 1000))
        assertFalse(FocusGuardPolicy.sameBoot(-1, -1, 1_000_000, 0, 9000, 1000))
    }

    @Test
    fun `blacklist and whitelist never block essentials or unknown modes`() {
        val apps = setOf("video", "reader", "phone", "settings", "launcher", "timer", "keyboard")
        val essential = setOf("phone", "settings", "launcher", "timer", "keyboard")

        fun block(
            pkg: String,
            mode: String = "blacklist",
            blocked: Set<String> = setOf("video"),
            allowed: Set<String> = setOf("reader"),
        ) = FocusGuardPolicy.shouldBlock(pkg, mode, blocked, allowed, apps, essential)

        assertTrue(FocusGuardPolicy.shouldBlock("video", "blacklist", setOf("video"), emptySet(), emptySet(), essential))
        assertTrue(block("video"))
        assertFalse(block("reader"))
        assertFalse(block("video", blocked = emptySet()))
        assertTrue(block("video", "whitelist"))
        assertFalse(block("reader", "whitelist"))
        essential.forEach { pkg ->
            assertFalse(block(pkg, blocked = apps))
            assertFalse(block(pkg, "whitelist", allowed = emptySet()))
        }
        assertFalse(block("permission-dialog", "whitelist", allowed = emptySet()))
        assertFalse(block("", "whitelist"))
        assertFalse(block("video", "unknown"))
    }
}
