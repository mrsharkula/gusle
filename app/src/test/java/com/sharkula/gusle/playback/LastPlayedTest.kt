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
    fun comesBackWithWhereTheSongsCameFrom() {
        val artist = queue.copy(source = QueueSource.Artist("sabaton"))
        lastPlayed().save(artist)
        assertEquals(artist, lastPlayed().loaded())
    }

    /** Saved before Gusle kept where the songs came from: they came from All songs. */
    @Test
    fun aQueueSavedWithoutWhereItCameFromHasNone() {
        lastPlayed().save(queue.copy(source = QueueSource.Artist("sabaton")))
        files.getValue("last_played_queue").values.remove("source")
        assertEquals(queue.copy(source = QueueSource.AllSongs), lastPlayed().loaded())
    }

    @Test
    fun writesTheSongsAgainWhenOnlyWhereTheyCameFromChanges() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue.copy(source = QueueSource.AllSongs))
        lastPlayed.save(queue.copy(source = QueueSource.Artist("sabaton")))
        assertEquals(2, files.getValue("last_played_queue").writes)
        assertEquals(1, files.getValue("last_played_position").writes)
        assertEquals(queue.copy(source = QueueSource.Artist("sabaton")), lastPlayed().loaded())
    }

    /** A playlist can have a song twice: it comes back on the one it was on, not the first. */
    @Test
    fun comesBackOnTheSameOneOfASongThatsThereTwice() {
        val playlist = SavedQueue(listOf(10L, 20L, 10L, 30L), null, current = 2, positionMs = 5_000, QueueSource.Playlist(1, "Road trip"))
        val lastPlayed = lastPlayed()
        lastPlayed.save(playlist)
        assertEquals(playlist, lastPlayed().loaded())
        // On to the first one: only where it is changes, but that's written too.
        lastPlayed.save(playlist.copy(current = 0))
        assertEquals(playlist.copy(current = 0), lastPlayed().loaded())
    }

    /** From a playlist, each song comes back as the entry it was, also after only some are left. */
    @Test
    fun comesBackWithThePlaylistsEntries() {
        val playlist = SavedQueue(
            listOf(10L, 20L, 10L),
            listOf(2, 0, 1),
            current = 2,
            positionMs = 5_000,
            QueueSource.Playlist(1, "Road trip"),
            entryIds = listOf(7L, 8L, 9L),
        )
        lastPlayed().save(playlist)
        assertEquals(playlist, lastPlayed().loaded())
        val left = runBlocking { lastPlayed().apply { forgetSong(20) }.loaded() }
        assertEquals(listOf(7L, 9L), left?.entryIds)
        assertEquals(listOf(7L, 9L), lastPlayed().loaded()?.entryIds)
        // From elsewhere: none.
        lastPlayed().save(queue)
        assertNull(lastPlayed().loaded()?.entryIds)
    }

    /** As the builds before playlists saved it, without which of its items it was on: the first. */
    @Test
    fun aSongThereTwiceSavedWithoutWhereItIsIsTheFirst() {
        lastPlayed().save(SavedQueue(listOf(10L, 20L, 10L), null, current = 2, positionMs = 5_000))
        files.getValue("last_played_position").values.remove("index")
        assertEquals(SavedQueue(listOf(10L, 20L, 10L), null, current = 0, positionMs = 5_000), lastPlayed().loaded())
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

    /** The queue plays 30, 10, 20 (shuffled); it was on 20 at 31.5 s. */
    @Test
    fun aDeletedSongLeavesTheSavedQueue() {
        lastPlayed().save(queue)
        val lastPlayed = lastPlayed()
        runBlocking { lastPlayed.forgetSong(30) }
        val left = SavedQueue(listOf(10L, 20L), shuffleOrder = listOf(0, 1), current = 1, positionMs = 31_500)
        assertEquals(left, lastPlayed.saved)
        // And on disk: Gusle started again comes back to it.
        assertEquals(left, lastPlayed().loaded())
    }

    @Test
    fun aDeletedSongItWasOnMovesItBackWhenItWasTheLast() {
        lastPlayed().save(queue)
        val lastPlayed = lastPlayed()
        runBlocking { lastPlayed.forgetSong(20) }
        // 20 was the last to play: back to 10, before it, from its start.
        assertEquals(SavedQueue(listOf(10L, 30L), shuffleOrder = listOf(1, 0), current = 0, positionMs = 0), lastPlayed().loaded())
    }

    @Test
    fun aDeletedSongThatWasTheOnlyOneForgetsTheQueue() {
        lastPlayed().save(SavedQueue(listOf(10L, 10L), shuffleOrder = null, current = 1, positionMs = 2_000))
        val lastPlayed = lastPlayed()
        runBlocking { lastPlayed.forgetSong(10) }
        assertNull(lastPlayed.saved)
        assertNull(lastPlayed().loaded())
    }

    @Test
    fun aDeletedSongThatIsNotInTheQueueWritesNothing() {
        lastPlayed().save(queue)
        val writes = files.values.sumOf { it.writes }
        runBlocking { lastPlayed().forgetSong(99) }
        assertEquals(writes, files.values.sumOf { it.writes })
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
