package com.pomodorotimer.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test fun comparesVersionComponentsNumerically() {
        assertTrue(isNewerVersion("1.0.11", "1.0.9"))
        assertTrue(isNewerVersion("1.1.0", "1.0.99"))
        assertFalse(isNewerVersion("1.0.9", "1.0.11"))
        assertFalse(isNewerVersion("1.0.11", "1.0.11"))
    }

    @Test fun ignoresInvalidAndPrereleaseTags() {
        assertFalse(isNewerVersion("1.0.12-beta", "1.0.11"))
        assertFalse(isNewerVersion("2.0", "1.0.11"))
        assertFalse(isNewerVersion("2.0.+1", "1.0.11"))
    }
}
