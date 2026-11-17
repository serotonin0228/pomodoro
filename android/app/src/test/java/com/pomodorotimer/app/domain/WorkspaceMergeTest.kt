package com.pomodorotimer.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceMergeTest {
    @Test fun pristineDeviceAdoptsCloudTimerAndSettings() {
        val local = initialState("local", now = 2000)
        val remote = initialState("remote", now = 1000).copy(settings = Settings.Default.copy(focusMinutes = 45))
        val merged = mergeWorkspace(local, remote)
        assertEquals(remote.settings, merged.settings)
        assertEquals(remote.timer, merged.timer)
    }

    @Test fun mergesHistoryAndKeepsNewerLocalTimer() {
        val oldRecord = FocusRecord("remote", Mode.COUNTDOWN, 1000, 2000, 1, listOf(Segment(1000, 2000)))
        val localRecord = FocusRecord("local", Mode.STOPWATCH, 3000, 4000, 1, listOf(Segment(3000, 4000)))
        val local = initialState("local", 8000).copy(records = listOf(localRecord), settings = Settings.Default.copy(focusMinutes = 30))
        val remote = initialState("remote", 5000).copy(records = listOf(oldRecord))
        val merged = mergeWorkspace(local, remote)
        assertEquals(setOf("local", "remote"), merged.records.map { it.id }.toSet())
        assertEquals(local.timer, merged.timer)
        assertEquals(local.settings, merged.settings)
    }
    @Test fun takeoverPreservesRunningTimeAndSession() {
        val original = initialState("a", 1000).let { it.copy(timer = it.timer.copy(status = TimerStatus.RUNNING, startedAt = 1000, elapsed = 30)) }
        val claimed = claimTimer(original, "b", 5000)
        assertEquals("b", claimed.timer.device)
        assertEquals(original.timer.copy(device = "b"), claimed.timer)
        assertEquals(original.segments, claimed.segments)
        assertEquals(true, isForeignActive(claimed.timer, "a"))
        assertEquals(false, isForeignActive(claimed.timer, "b"))
    }

    @Test fun remoteTimerPreservesLocalRecordsAndSettings() {
        val record = FocusRecord("local", Mode.STOPWATCH, 3000, 4000, 1, listOf(Segment(3000, 4000)))
        val local = initialState("a", 8000).copy(records = listOf(record), settings = Settings.Default.copy(focusMinutes = 30))
        val remote = initialState("b", 5000).let { it.copy(timer = it.timer.copy(status = TimerStatus.RUNNING, startedAt = 5000)) }
        val result = mergeRemoteTimer(local, remote, true)
        assertEquals(remote.timer, result.timer)
        assertEquals(local.settings, result.settings)
        assertEquals(listOf(record), result.records)
    }
}
