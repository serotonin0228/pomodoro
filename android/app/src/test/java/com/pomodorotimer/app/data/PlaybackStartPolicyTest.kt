package com.pomodorotimer.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStartPolicyTest {
    @Test fun startingFocusKeepsCurrentlyPlayingAudioInPlace() {
        assertEquals(PlaybackStartPolicy.Action.KEEP_PLAYING, PlaybackStartPolicy.decide(hasPlayer = true, isPlaying = true))
    }

    @Test fun startingFocusResumesPausedAudioWithoutReloading() {
        assertEquals(PlaybackStartPolicy.Action.RESUME, PlaybackStartPolicy.decide(hasPlayer = true, isPlaying = false))
    }

    @Test fun startingFocusLoadsQueueOnlyWithoutExistingPlayer() {
        assertEquals(PlaybackStartPolicy.Action.START_QUEUE, PlaybackStartPolicy.decide(hasPlayer = false, isPlaying = false))
    }
}
