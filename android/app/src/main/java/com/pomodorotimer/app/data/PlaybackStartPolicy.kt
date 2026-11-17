package com.pomodorotimer.app.data

/** Starting a focus timer should preserve an existing player's position. */
object PlaybackStartPolicy {
    enum class Action { KEEP_PLAYING, RESUME, START_QUEUE }
    fun decide(hasPlayer: Boolean, isPlaying: Boolean): Action = when {
        !hasPlayer -> Action.START_QUEUE
        isPlaying -> Action.KEEP_PLAYING
        else -> Action.RESUME
    }
}
