package com.sharkula.gusle.data

import android.net.TestUri
import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    /** The file on "disk"; a new [Playlists] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun playlists() = Playlists { file }

    private fun Playlists.loaded() = runBlocking { load() }

    private fun Playlists.created(name: String) = runBlocking { create(name) }

    private fun song(id: Long, durationMs: Long = 200_000 + id) = Song(id, TestUri, "Song $id", "Sabaton", durationMs, 0)

    private val songs = (1L..5L).map { song(it) }.associateBy { it.id }

    /** Each entry made here gets the next ID, in any playlist, as Gusle gives them. */
    private var lastEntryId = 100L

    /** The song with [songId], as an entry of its own. */
    private fun entry(songId: Long) = PlaylistEntry(++lastEntryId, songId, songs.getValue(songId).durationMs)

    /** A playlist called [name] with [entries], as an earlier run of Gusle wrote it. */
    private fun saved(id: Long, name: String, vararg entries: PlaylistEntry) {
        file.values[id.toString()] = entries.joinToString(",") { "${it.id},${it.songId},${it.durationMs}" } + "\n" + name
    }

    private fun Playlist.songIds() = songsIn(songs).map { it.song.id }

    @Test
    fun hasNoneAtFirst() {
        assertTrue(playlists().loaded().isEmpty())
    }

    @Test
    fun areNotKnownBeforeTheRead() {
        assertNull(Playlists { file }.all.value)
    }

    @Test
    fun aNewOneIsEmptyAndComesBackAfterARestart() {
        val playlists = playlists()
        val id = playlists.created("Road trip")
        assertEquals(listOf(Playlist(id, "Road trip", emptyList())), playlists.all.value)
        assertEquals(listOf(Playlist(id, "Road trip", emptyList())), playlists().loaded())
    }

    @Test
    fun theOldestComesFirst() {
        val playlists = playlists()
        playlists.created("Workout")
        playlists.created("Road trip")
        playlists.created("Calm")
        assertEquals(listOf("Workout", "Road trip", "Calm"), playlists().loaded().map { it.name })
    }

    /** E.g. once playlists can be deleted: a new one never takes a deleted one's ID. */
    @Test
    fun anIdIsNeverGivenTwice() {
        val playlists = playlists()
        playlists.created("Road trip")
        val second = playlists.created("Workout")
        file.values.remove(second.toString())
        val third = playlists().created("Calm")
        assertTrue(third > second)
    }

    @Test
    fun aNameKeepsWhateverItHolds() {
        val name = "Ljuta: \"gusle\", 2,3\nnew line"
        val id = playlists().created(name)
        assertEquals(name, playlists().loaded().single { it.id == id }.name)
    }

    @Test
    fun theSongsAreInTheOrderTheyWereAddedAndASongCanBeTwice() {
        val entries = arrayOf(entry(3), entry(1), entry(3))
        saved(1, "Road trip", *entries)
        val playlist = playlists().loaded().single()
        assertEquals(listOf(3L, 1L, 3L), playlist.songIds())
        // Each with its own entry: the two of song 3 are told apart.
        assertEquals(entries.map { it.id }, playlist.songsIn(songs).map { it.entryId })
        assertEquals(3, playlist.countIn(songs))
    }

    /** E.g. while a file is copied again: it's back in its place. */
    @Test
    fun aSongMissingFromTheWatchIsOutOfSightButStays() {
        val entries = arrayOf(entry(2), entry(4), entry(5))
        saved(1, "Road trip", *entries)
        val playlists = playlists()
        val onWatch = songs - 2L
        playlists.loaded()
        playlists.matchWith(onWatch)
        val playlist = playlists.all.value!!.single()
        assertEquals(listOf(4L, 5L), playlist.songsIn(onWatch).map { it.song.id })
        assertEquals(listOf(entries[1].id, entries[2].id), playlist.songsIn(onWatch).map { it.entryId })
        assertEquals(2, playlist.countIn(onWatch))
        assertEquals(4L, playlist.firstSongIn(onWatch)?.id)
        assertEquals(listOf(2L, 4L, 5L), playlists().loaded().single().songIds())
    }

    @Test
    fun anEmptyOneHasNoFirstSong() {
        saved(1, "Road trip")
        assertNull(playlists().loaded().single().firstSongIn(songs))
    }

    /** MediaStore gave a song added later the ID of a deleted one. */
    @Test
    fun anotherSongWithTheIdOfOneInAPlaylistIsntInIt() {
        val three = entry(3)
        saved(1, "Road trip", entry(2), three)
        val onWatch = songs + (2L to song(2, durationMs = 95_000))
        val playlists = playlists()
        playlists.loaded()
        assertEquals(listOf(3L), playlists.all.value!!.single().songsIn(onWatch).map { it.song.id })
        playlists.matchWith(onWatch)
        assertEquals(listOf(three), playlists().loaded().single().entries)
    }

    /** MediaStore may work a length out a little differently, e.g. after an update. */
    @Test
    fun aLengthALittleOffIsTheSameSong() {
        saved(1, "Road trip", entry(2))
        val onWatch = songs + (2L to song(2, durationMs = songs.getValue(2).durationMs + 400))
        val playlists = playlists()
        playlists.loaded()
        playlists.matchWith(onWatch)
        assertEquals(listOf(2L), playlists.all.value!!.single().songsIn(onWatch).map { it.song.id })
    }

    @Test
    fun matchingChangesNothingWhenEverySongIsThere() {
        saved(1, "Road trip", entry(2), entry(3))
        val playlists = playlists()
        playlists.loaded()
        val writes = file.writes
        playlists.matchWith(songs)
        assertEquals(writes, file.writes)
    }

    /** Deleted from the watch: it leaves every playlist, every time it's there; the rest keep their entries. */
    @Test
    fun aForgottenSongLeavesEveryPlaylist() {
        val three = entry(3)
        val four = entry(4)
        saved(1, "Road trip", entry(2), three, entry(2))
        saved(2, "Workout", entry(2))
        saved(3, "Calm", four)
        val playlists = playlists()
        playlists.loaded()
        playlists.forget(2)
        val expected = listOf(listOf(three), emptyList(), listOf(four))
        assertEquals(expected, playlists.all.value!!.map { it.entries })
        assertEquals(expected, playlists().loaded().map { it.entries })
    }

    @Test
    fun aSongForgottenBeforeTheReadIsGoneOnceRead() {
        val two = entry(2)
        saved(1, "Road trip", two, entry(3))
        val playlists = playlists()
        playlists.forget(3)
        assertEquals(listOf(two), playlists.loaded().single().entries)
        assertEquals(listOf(two), playlists().loaded().single().entries)
    }

    @Test
    fun damagedOnesAreLeftOut() {
        val two = entry(2)
        saved(1, "Road trip", two)
        file.values["2"] = "no line break"
        file.values["3"] = true
        file.values["not a playlist"] = "1,2,3\nCalm"
        // A damaged entry, one whose ID another one has, and lone numbers are left out of it.
        val three = entry(3)
        file.values["4"] = "7,x,1,${three.id},3,${three.durationMs},${two.id},2,${two.durationMs},5,6\nWorkout"
        file.values["next_id"] = "soon"
        val playlists = playlists().loaded()
        assertEquals(listOf(1L, 4L), playlists.map { it.id })
        assertEquals(listOf(three), playlists[1].entries)
        assertEquals(5L, playlists().created("New"))
    }
}
