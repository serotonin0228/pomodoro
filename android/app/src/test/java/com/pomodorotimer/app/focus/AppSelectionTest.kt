package com.pomodorotimer.app.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSelectionTest {
    private val apps = listOf("a" to "Apple", "b" to "Beta", "c" to "Chat")
    @Test fun selectedAppsAppearFirstInAlphabeticalOrder() {
        assertEquals(listOf("b", "c", "a"), AppSelection.order(apps, setOf("b", "c")).map { it.first })
    }
    @Test fun searchAndBulkActionsOnlyAffectVisibleApps() {
        val visible = AppSelection.filter(apps, "bEt").map { it.first }.toSet()
        assertEquals(setOf("a", "b"), AppSelection.selectAll(setOf("a"), visible))
        assertEquals(setOf("a"), AppSelection.invert(setOf("a", "b"), visible))
        assertEquals(setOf("a", "b"), AppSelection.invert(setOf("a"), visible))
    }
}
