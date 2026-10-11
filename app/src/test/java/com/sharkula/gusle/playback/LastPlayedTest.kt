package com.sharkula.gusle.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastPlayedTest {
    /** The files on "disk", by name; a new [LastPlayed] on them is Gusle started again. */
    private val files = HashMap<String, FakePrefs>()

    private fun lastPlayed() = LastPlayed { name -> files.getOrPut(name) { FakePrefs() } }

    private fun LastPlayed.loaded() = runBlocking { load() }

    private val queue = SavedQueue(listOf(10L, 20L, 30L), shuffleOrder = listOf(2, 0, 1), current = 1, positionMs = 31_500)

    @Test
    fun comesBackAfterARestart() {
        lastPlayed().save(queue)
        assertEquals(queue, lastPlayed().loaded())
    }

    @Test
    fun hasNothingBeforeAnythingIsSaved() {
        assertNull(lastPlayed().loaded())
    }

    @Test
    fun writesOnlyThePositionWhileTheSongsStayTheSame() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.save(queue.copy(positionMs = 41_500))
        lastPlayed.save(queue.copy(positionMs = 41_500))
        assertEquals(1, files.getValue("last_played_queue").writes)
        assertEquals(2, files.getValue("last_played_position").writes)
        assertEquals(queue.copy(positionMs = 41_500), lastPlayed().loaded())
    }

    @Test
    fun forgetsTheShuffleOrderWhenShuffleGoesOff() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.save(queue.copy(shuffleOrder = null))
        assertEquals(queue.copy(shuffleOrder = null), lastPlayed().loaded())
    }

    @Test
    fun forgetsEverything() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.forget()
        assertNull(lastPlayed.loaded())
        assertNull(lastPlayed().loaded())
        // And a later save stores the songs again, not only where it is.
        lastPlayed.save(queue)
        assertEquals(queue, lastPlayed().loaded())
    }

    @Test
    fun aSaveBeforeTheReadIsNewer() {
        lastPlayed().save(queue)
        val lastPlayed = lastPlayed()
        val newer = queue.copy(current = 2, positionMs = 0)
        lastPlayed.save(newer)
        assertEquals(newer, lastPlayed.loaded())
    }

    @Test
    fun aValueOfAnotherTypeReadsAsNothing() {
        lastPlayed().save(queue)
        files.getValue("last_played_position").values["song_id"] = "20"
        assertNull(lastPlayed().loaded())
    }

    /**
     * The order is saved as the songs' indices in the order they play, and restored with
     * DefaultShuffleOrder, which must then play them in that order.
     */
    @OptIn(UnstableApi::class)
    @Test
    fun aRestoredShuffleOrderPlaysAsSaved() {
        val saved = listOf(3, 0, 4, 2, 1)
        val restored = DefaultShuffleOrder(saved.toIntArray(), 7)
        val plays = generateSequence(restored.firstIndex) { restored.getNextIndex(it).takeIf { next -> next != C.INDEX_UNSET } }
        assertEquals(saved, plays.toList())
    }
}
