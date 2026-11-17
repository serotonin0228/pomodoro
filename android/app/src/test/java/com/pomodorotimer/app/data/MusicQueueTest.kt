package com.pomodorotimer.app.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Queue rules that the player and the UI both rely on. */
class MusicQueueTest {

    @Test
    fun `empty catalogue produces no order`() {
        assertTrue(MusicQueue.order(0, com.pomodorotimer.app.domain.MusicMode.SEQUENCE).isEmpty())
    }

    @Test
    fun `sequence order is stable`() {
        assertEquals(listOf(0, 1, 2), MusicQueue.order(3, com.pomodorotimer.app.domain.MusicMode.SEQUENCE))
    }

    @Test
    fun `shuffle keeps every track exactly once`() {
        val order = MusicQueue.order(50, com.pomodorotimer.app.domain.MusicMode.SHUFFLE, Random(7))
        assertEquals(50, order.size)
        assertEquals((0 until 50).toSet(), order.toSet())
    }

    @Test
    fun `single repeats the current position`() {
        val order = listOf(2, 0, 1)
        assertEquals(1, MusicQueue.advance(1, order, com.pomodorotimer.app.domain.MusicMode.SINGLE))
        assertEquals(1, MusicQueue.previous(1, order, com.pomodorotimer.app.domain.MusicMode.SINGLE))
    }

    @Test
    fun `queue wraps around in both directions`() {
        val order = listOf(2, 0, 1)
        assertEquals(0, MusicQueue.advance(2, order, com.pomodorotimer.app.domain.MusicMode.SEQUENCE))
        assertEquals(2, MusicQueue.previous(0, order, com.pomodorotimer.app.domain.MusicMode.SEQUENCE))
        assertEquals(1, MusicQueue.advance(0, order, com.pomodorotimer.app.domain.MusicMode.SHUFFLE))
    }

    @Test
    fun `unknown track id falls back to the first position`() {
        val tracks = listOf(MusicTrack("a", "A", "", 1), MusicTrack("b", "B", "", 1))
        val order = listOf(1, 0)
        assertEquals(0, MusicQueue.indexOf(order, tracks, "b"))
        assertEquals(1, MusicQueue.indexOf(order, tracks, "a"))
        assertEquals(0, MusicQueue.indexOf(order, tracks, "missing"))
        assertEquals(0, MusicQueue.indexOf(order, tracks, null))
    }
    @Test
    fun `automatic single repeats while manual next advances`() {
        val order = listOf(0, 1, 2)
        assertEquals(1, MusicQueue.nextIndex(1, order, com.pomodorotimer.app.domain.MusicMode.SINGLE, automatic = true))
        assertEquals(2, MusicQueue.nextIndex(1, order, com.pomodorotimer.app.domain.MusicMode.SINGLE, automatic = false))
        assertEquals(0, MusicQueue.nextIndex(2, order, com.pomodorotimer.app.domain.MusicMode.SEQUENCE, automatic = true))
    }

    @Test
    fun `shuffle advances through every downloaded track before repeating`() {
        val order = MusicQueue.order(4, com.pomodorotimer.app.domain.MusicMode.SHUFFLE, Random(7))
        var current = order.first()
        val visited = mutableListOf(current)
        repeat(3) {
            current = MusicQueue.nextIndex(current, order, com.pomodorotimer.app.domain.MusicMode.SHUFFLE, automatic = true)
            visited.add(current)
        }
        assertEquals(order.toSet(), visited.toSet())
        assertEquals(order.first(), MusicQueue.nextIndex(current, order, com.pomodorotimer.app.domain.MusicMode.SHUFFLE, automatic = true))
    }
}
