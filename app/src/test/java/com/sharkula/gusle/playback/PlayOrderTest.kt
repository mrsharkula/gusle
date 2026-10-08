package com.sharkula.gusle.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayOrderTest {
    // Plays 2, 0, 3, 1, 4.
    private val shuffled = listOf(2, 0, 3, 1, 4)

    @Test
    fun goesOnToTheNextSongInPlayOrder() {
        assertEquals(3, nextPlayable(shuffled, from = 0, backwards = false) { true })
        assertEquals(1, nextPlayable(listOf(0, 1, 2), from = 0, backwards = false) { true })
    }

    @Test
    fun skipsSongsThatCantBePlayed() {
        assertEquals(4, nextPlayable(shuffled, from = 0, backwards = false) { it != 3 && it != 1 })
    }

    @Test
    fun goesBackAfterPrevious() {
        assertEquals(0, nextPlayable(shuffled, from = 3, backwards = true) { true })
        assertEquals(2, nextPlayable(shuffled, from = 3, backwards = true) { it != 0 })
    }

    @Test
    fun stopsWhenNoSongIsLeftThatWay() {
        // The last song, and the first one going back.
        assertNull(nextPlayable(shuffled, from = 4, backwards = false) { true })
        assertNull(nextPlayable(shuffled, from = 2, backwards = true) { true })
        // Every other song can't be played either.
        assertNull(nextPlayable(shuffled, from = 2, backwards = false) { false })
        assertNull(nextPlayable(listOf(0), from = 0, backwards = false) { true })
    }

    @Test
    fun stopsWhenTheSongIsntInTheOrder() {
        assertNull(nextPlayable(shuffled, from = 7, backwards = false) { true })
        assertNull(nextPlayable(emptyList(), from = 0, backwards = true) { true })
    }
}
