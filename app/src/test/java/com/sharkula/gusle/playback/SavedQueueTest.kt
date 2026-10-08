package com.sharkula.gusle.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SavedQueueTest {
    private fun SavedQueue.storedAndRead() =
        SavedQueue.of(songIds.joinedNumbers(), shuffleOrder?.joinedNumbers(), currentSongId, positionMs)

    @Test
    fun readsBackWhatWasStored() {
        val aToZ = SavedQueue(listOf(30L, 12L, 7L), shuffleOrder = null, current = 1, positionMs = 31_500)
        assertEquals(aToZ, aToZ.storedAndRead())
        val shuffled = SavedQueue(listOf(30L, 12L, 7L), shuffleOrder = listOf(2, 0, 1), current = 2, positionMs = 0)
        assertEquals(shuffled, shuffled.storedAndRead())
    }

    @Test
    fun readsNothingFromAnEmptyOrDamagedSave() {
        assertNull(SavedQueue.of(null, null, 1, 0))
        assertNull(SavedQueue.of("", null, 1, 0))
        assertNull(SavedQueue.of("1,x,3", null, 1, 0))
        assertNull(SavedQueue.of("1,2,3", null, 1, -1))
        // Not every song once.
        assertNull(SavedQueue.of("1,2,3", "0,1", 1, 0))
        assertNull(SavedQueue.of("1,2,3", "0,1,1", 1, 0))
        assertNull(SavedQueue.of("1,2,3", "0,1,3", 1, 0))
    }

    /** The song and the queue are stored apart; if they don't match, the queue starts over. */
    @Test
    fun startsOverWhenTheSongIsntInTheQueue() {
        assertEquals(SavedQueue(listOf(1L, 2L, 3L), null, 0, 0), SavedQueue.of("1,2,3", null, 9, 5_000))
        assertEquals(SavedQueue(listOf(1L, 2L, 3L), listOf(2, 0, 1), 2, 0), SavedQueue.of("1,2,3", "2,0,1", 9, 5_000))
    }

    @Test
    fun keepsItAsItIsWhenEverySongIsThere() {
        val queue = SavedQueue(listOf(1L, 2L, 3L), listOf(2, 0, 1), current = 0, positionMs = 5_000)
        assertSame(queue, queue.keepOnly { true })
    }

    @Test
    fun forgetsItWhenNoSongIsLeft() {
        assertNull(SavedQueue(listOf(1L, 2L), null, 0, 0).keepOnly { false })
    }

    @Test
    fun leavesOutGoneSongsAndStaysOnTheSameSong() {
        val queue = SavedQueue(listOf(10L, 20L, 30L, 40L), shuffleOrder = null, current = 2, positionMs = 5_000)
        assertEquals(
            SavedQueue(listOf(10L, 30L, 40L), shuffleOrder = null, current = 1, positionMs = 5_000),
            queue.keepOnly { it != 20L },
        )
    }

    @Test
    fun keepsTheShuffledOrderOfTheSongsLeft() {
        // Plays 30, 10, 40, 20; 20 is gone.
        val queue = SavedQueue(listOf(10L, 20L, 30L, 40L), shuffleOrder = listOf(2, 0, 3, 1), current = 3, positionMs = 5_000)
        assertEquals(
            // Plays 30, 10, 40.
            SavedQueue(listOf(10L, 30L, 40L), shuffleOrder = listOf(1, 0, 2), current = 2, positionMs = 5_000),
            queue.keepOnly { it != 20L },
        )
    }

    @Test
    fun movesOnToTheNextSongLeftFromItsStart() {
        // A–Z: on 20, and 20 and 30 are gone.
        val aToZ = SavedQueue(listOf(10L, 20L, 30L, 40L), shuffleOrder = null, current = 1, positionMs = 5_000)
        assertEquals(
            SavedQueue(listOf(10L, 40L), shuffleOrder = null, current = 1, positionMs = 0),
            aToZ.keepOnly { it != 20L && it != 30L },
        )
        // Shuffled, playing 30, 10, 40, 20: on 10, which is gone, so next is 40.
        val shuffled = SavedQueue(listOf(10L, 20L, 30L, 40L), shuffleOrder = listOf(2, 0, 3, 1), current = 0, positionMs = 5_000)
        assertEquals(
            SavedQueue(listOf(20L, 30L, 40L), shuffleOrder = listOf(1, 2, 0), current = 2, positionMs = 0),
            shuffled.keepOnly { it != 10L },
        )
    }

    @Test
    fun takesTheLastSongLeftWhenNoneFollows() {
        // Shuffled, playing 30, 10, 40, 20: on 20, the last, which is gone, so 40.
        val queue = SavedQueue(listOf(10L, 20L, 30L, 40L), shuffleOrder = listOf(2, 0, 3, 1), current = 1, positionMs = 5_000)
        assertEquals(
            SavedQueue(listOf(10L, 30L, 40L), shuffleOrder = listOf(1, 0, 2), current = 2, positionMs = 0),
            queue.keepOnly { it != 20L },
        )
    }
}
