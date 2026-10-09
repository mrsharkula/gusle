package com.sharkula.gusle.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerQueueTest {
    // The player's items play songs 10, 20, 30.
    private val songIds = listOf(10L, 20L, 30L)

    @Test
    fun listsTheSongsInPlayOrder() {
        // Shuffled, they play 30, 10, 20.
        val queue = PlayerQueue.of(listOf(2, 0, 1), shuffled = true, songIds::get) { true }
        assertEquals(listOf(30L, 10L, 20L), queue.songs.map { it.songId })
        assertEquals(listOf(2, 0, 1), queue.songs.map { it.window })
        assertEquals(listOf(0, 1, 2), queue.songs.map { it.place })
        assertEquals(1, queue.placeOf(0))
        assertEquals(0, queue.placeOf(2))
        assertEquals(-1, queue.placeOf(3))
    }

    @Test
    fun leavesOutSongsNoLongerOnTheWatch() {
        val queue = PlayerQueue.of(listOf(0, 1, 2), shuffled = false, songIds::get) { it != 20L }
        assertEquals(listOf(10L, 30L), queue.songs.map { it.songId })
        // The others keep their place, so the ones before the player's song still count as played.
        assertEquals(listOf(0, 2), queue.songs.map { it.place })
        assertEquals(1, queue.placeOf(1))
    }

    @Test
    fun equalsTheSameQueueBuiltAgain() {
        assertEquals(PlayerQueue.of(listOf(1, 0), shuffled = true, songIds::get) { true }, PlayerQueue.of(listOf(1, 0), shuffled = true, songIds::get) { true })
    }
}
