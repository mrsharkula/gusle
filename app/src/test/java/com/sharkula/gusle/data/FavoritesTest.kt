package com.sharkula.gusle.data

import android.net.TestUri
import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    /** The file on "disk"; a new [Favorites] on it is Gusle started again. */
    private val file = FakePrefs()

    private var now = 1_000L
    private var changes = 0

    private fun favorites() = Favorites(onChange = { changes++ }, clock = { now++ }) { file }

    private fun Favorites.loaded() = runBlocking { load() }

    private fun song(id: Long, durationMs: Long = 200_000 + id) = Song(id, TestUri, "Song $id", "Sabaton", durationMs, 0)

    private val songs = (1L..5L).map { song(it) }.associateBy { it.id }

    private fun song(id: Int) = songs.getValue(id.toLong())

    private fun FavoriteSongs.ids() = newestFirst(songs).map { it.id }

    @Test
    fun hasNoneAtFirst() {
        assertTrue(favorites().loaded().isEmpty())
    }

    @Test
    fun comeBackAfterARestart() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(3), true)
        favorites.set(song(1), true)
        val again = favorites().loaded()
        assertTrue(song(3) in again)
        assertTrue(song(1) in again)
        assertFalse(song(2) in again)
    }

    @Test
    fun theNewestComesFirst() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        favorites.set(song(5), true)
        favorites.set(song(1), true)
        assertEquals(listOf(1L, 5L, 2L), favorites().loaded().ids())
    }

    @Test
    fun addedAgainComesFirstAgain() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        favorites.set(song(5), true)
        favorites.set(song(2), false)
        favorites.set(song(2), true)
        assertEquals(listOf(2L, 5L), favorites.songs.value.ids())
    }

    /** E.g. a watch that started with a wrong clock, set right later. */
    @Test
    fun theOrderIsTheOrderTheyWereAddedInWhenTheClockGoesBack() {
        val favorites = favorites()
        favorites.loaded()
        now = 9_000_000
        favorites.set(song(2), true)
        now = 1_000
        favorites.set(song(5), true)
        favorites.set(song(1), true)
        assertEquals(listOf(1L, 5L, 2L), favorites().loaded().ids())
    }

    @Test
    fun aRemovedSongIsGone() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(4), true)
        favorites.set(song(4), false)
        assertFalse(song(4) in favorites.songs.value)
        assertFalse(song(4) in favorites().loaded())
    }

    @Test
    fun aSongAddedTwiceKeepsWhenItWasFirstAdded() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        favorites.set(song(5), true)
        favorites.set(song(2), true)
        assertEquals(listOf(5L, 2L), favorites.songs.value.ids())
        assertEquals(2, file.writes)
        assertEquals(2, changes)
    }

    @Test
    fun aChangeBeforeTheReadGoesOverIt() {
        val first = favorites()
        first.loaded()
        first.set(song(1), true)
        first.set(song(2), true)
        val writes = file.writes
        val favorites = favorites()
        favorites.set(song(1), false)
        favorites.set(song(3), true)
        // Nothing's written before the file is read.
        assertEquals(writes, file.writes)
        assertEquals(listOf(3L, 2L), favorites.loaded().ids())
        assertEquals(listOf(3L, 2L), favorites().loaded().ids())
    }

    /** E.g. the heart tapped right after a start, still outlined while the favorites were read. */
    @Test
    fun aFavoriteAddedAgainBeforeTheReadKeepsWhenItWasFirstAdded() {
        val first = favorites()
        first.loaded()
        first.set(song(2), true)
        first.set(song(5), true)
        val favorites = favorites()
        favorites.set(song(2), true)
        assertEquals(listOf(5L, 2L), favorites.loaded().ids())
        assertEquals(listOf(5L, 2L), favorites().loaded().ids())
    }

    @Test
    fun onlyTheSongsOnTheWatchAreShownAndCounted() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        favorites.set(song(9L), true)
        assertEquals(listOf(2L), favorites.songs.value.ids())
        assertEquals(1, favorites.songs.value.countIn(songs))
    }

    /** E.g. while a file is copied again: it's back with its favorite. */
    @Test
    fun aSongMissingFromTheWatchKeepsItsFavorite() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        val writes = file.writes
        favorites.matchWith(songs - 2L)
        favorites.matchWith(songs - 2L)
        assertEquals(writes, file.writes)
        assertTrue(song(2) in favorites().loaded())
    }

    /** MediaStore gave a song added later the ID of a deleted one. */
    @Test
    fun anotherSongWithTheIdOfAFavoriteIsntOne() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        val newcomer = song(2, durationMs = 95_000)
        val onWatch = songs + (2L to newcomer)
        assertFalse(newcomer in favorites.songs.value)
        assertEquals(0, favorites.songs.value.countIn(onWatch))
        favorites.matchWith(onWatch)
        assertFalse(song(2) in favorites().loaded())
    }

    /** MediaStore may work a length out a little differently, e.g. after an update. */
    @Test
    fun aLengthALittleOffIsTheSameSong() {
        val favorites = favorites()
        favorites.loaded()
        favorites.set(song(2), true)
        assertTrue(song(2, durationMs = song(2).durationMs + 400) in favorites.songs.value)
    }

    /** As the build before lengths kept them: the time alone. */
    @Test
    fun aFavoriteKeptWithoutItsLengthGetsIt() {
        file.values["2"] = 5_000L
        val favorites = favorites()
        assertTrue(song(2, durationMs = 1) in favorites.loaded())
        favorites.matchWith(songs)
        assertTrue(song(2) in favorites().loaded())
        assertFalse(song(2, durationMs = 1) in favorites().loaded())
    }

    @Test
    fun damagedEntriesAreLeftOut() {
        file.values["2"] = "5000,200002"
        file.values["not a song"] = "6000,1"
        file.values["3"] = "yesterday"
        file.values["4"] = true
        assertEquals(listOf(2L), favorites().loaded().ids())
    }
}
