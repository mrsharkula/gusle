package com.sharkula.gusle.playback

import com.sharkula.gusle.playback.AfterDeleting.NothingLeft
import com.sharkula.gusle.playback.AfterDeleting.Remove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletedSongTest {
    private val aToZ = listOf(0, 1, 2, 3, 4)

    @Test
    fun leavesAQueueWithoutTheSongAlone() {
        assertNull(afterDeleting(listOf(10L, 20L, 30L), listOf(0, 1, 2), current = 1, deleted = 99))
    }

    @Test
    fun removesASongItIsNotOnAndStaysWhereItIs() {
        assertEquals(
            Remove(listOf(3), moveTo = null, pause = false),
            afterDeleting(listOf(10L, 20L, 30L, 40L, 50L), aToZ, current = 1, deleted = 40),
        )
    }

    @Test
    fun goesToTheNextSongWhenItWasOnIt() {
        assertEquals(
            Remove(listOf(1), moveTo = 2, pause = false),
            afterDeleting(listOf(10L, 20L, 30L, 40L, 50L), aToZ, current = 1, deleted = 20),
        )
    }

    /** From a playlist later: the same song twice, both go, and it doesn't move on to the other one. */
    @Test
    fun removesEveryItemOfASongThatIsInItTwice() {
        val twice = listOf(10L, 20L, 20L, 30L, 20L)
        assertEquals(Remove(listOf(4, 2, 1), moveTo = 3, pause = false), afterDeleting(twice, aToZ, current = 1, deleted = 20))
        assertEquals(Remove(listOf(4, 2, 1), moveTo = 3, pause = false), afterDeleting(twice, aToZ, current = 2, deleted = 20))
        assertEquals(Remove(listOf(4, 2, 1), moveTo = null, pause = false), afterDeleting(twice, aToZ, current = 0, deleted = 20))
    }

    @Test
    fun goesBackToTheSongBeforeItPausedWhenItWasTheLast() {
        assertEquals(
            Remove(listOf(4), moveTo = 3, pause = true),
            afterDeleting(listOf(10L, 20L, 30L, 40L, 50L), aToZ, current = 4, deleted = 50),
        )
        // Also when the songs after it are the same song.
        assertEquals(
            Remove(listOf(4, 3), moveTo = 2, pause = true),
            afterDeleting(listOf(10L, 20L, 30L, 50L, 50L), aToZ, current = 3, deleted = 50),
        )
    }

    @Test
    fun followsTheShuffledOrder() {
        val songs = listOf(10L, 20L, 30L, 40L)
        val shuffled = listOf(2, 0, 3, 1)
        // On 10, whose next is 40 (index 3) in the shuffled order, not 20.
        assertEquals(Remove(listOf(0), moveTo = 3, pause = false), afterDeleting(songs, shuffled, current = 0, deleted = 10))
        // On 20, the last one shuffled: back to 40, before it.
        assertEquals(Remove(listOf(1), moveTo = 3, pause = true), afterDeleting(songs, shuffled, current = 1, deleted = 20))
    }

    @Test
    fun stopsWhenItWasTheOnlySong() {
        assertEquals(NothingLeft, afterDeleting(listOf(10L), listOf(0), current = 0, deleted = 10))
        assertEquals(NothingLeft, afterDeleting(listOf(10L, 10L), listOf(0, 1), current = 1, deleted = 10))
    }

    /** It lands where a saved queue without the song comes back to. */
    @Test
    fun agreesWithASavedQueueWithoutTheSong() {
        val songs = listOf(10L, 20L, 30L, 40L)
        for (shuffleOrder in listOf(null, listOf(2, 0, 3, 1))) {
            for (current in songs.indices) {
                for (deleted in songs) {
                    val saved = SavedQueue(songs, shuffleOrder, current, positionMs = 5_000)
                    val after = afterDeleting(songs, shuffleOrder ?: songs.indices.toList(), current, deleted) as Remove
                    val on = after.moveTo ?: current
                    val left = saved.keepOnly { it != deleted }!!
                    assertEquals("$shuffleOrder on $current without $deleted", songs[on], left.currentSongId)
                    assertEquals(if (after.moveTo == null) 5_000L else 0L, left.positionMs)
                }
            }
        }
    }
}
