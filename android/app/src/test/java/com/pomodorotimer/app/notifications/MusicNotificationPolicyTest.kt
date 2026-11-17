package com.pomodorotimer.app.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicNotificationPolicyTest {
    @Test fun mediaControlsFollowBothNotificationSettings() {
        assertTrue(MusicNotificationPolicy.showMediaControls(true, true))
        assertFalse(MusicNotificationPolicy.showMediaControls(true, false))
        assertFalse(MusicNotificationPolicy.showMediaControls(false, true))
        assertFalse(MusicNotificationPolicy.showMediaControls(false, false))
    }
}
