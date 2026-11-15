package com.serotonin.pomodoro.data

import com.serotonin.pomodoro.domain.MusicMode
import kotlin.random.Random

/**
 * Playback order, computed up front.
 *
 * Computing the order once (instead of asking "what is next?" at every boundary) is what the
 * reference app does too: a shuffle cannot repeat a track until the list is exhausted, and the
 * native player never has to wait for a decision while the process is in the background.
 */
object MusicQueue {

    /** Indices to play. `size` is the number of playable tracks. */
    fun order(size: Int, mode: MusicMode, random: Random = Random.Default): List<Int> {
        if (size <= 0) return emptyList()
        val indices = (0 until size).toMutableList()
        if (mode == MusicMode.SHUFFLE) {
            for (i in indices.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = indices[i]
                indices[i] = indices[j]
                indices[j] = tmp
            }
        }
        return indices
    }

    /**
     * Next position after [position].
     *
     * SINGLE repeats the same track; SEQUENCE and SHUFFLE walk the order and wrap around, which is
     * the audible equivalent of the player's `loop = all` in single/queue modes.
     */
    fun advance(position: Int, order: List<Int>, mode: MusicMode): Int {
        if (order.isEmpty()) return -1
        if (mode == MusicMode.SINGLE) return position.coerceIn(0, order.lastIndex)
        val current = position.coerceIn(-1, order.lastIndex)
        return if (current >= order.lastIndex) 0 else current + 1
    }

    fun previous(position: Int, order: List<Int>, mode: MusicMode): Int {
        if (order.isEmpty()) return -1
        if (mode == MusicMode.SINGLE) return position.coerceIn(0, order.lastIndex)
        val current = position.coerceIn(0, order.lastIndex)
        return if (current <= 0) order.lastIndex else current - 1
    }

    /** Position of a track id within the order, or 0 when it is unknown. */
    fun indexOf(order: List<Int>, tracks: List<MusicTrack>, trackId: String?): Int {
        if (trackId == null) return 0
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) return 0
        val position = order.indexOf(trackIndex)
        return if (position < 0) 0 else position
    }
}
