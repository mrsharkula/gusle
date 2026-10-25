package com.sharkula.gusle.data

import android.net.TestUri
import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    /** The file on "disk"; a new [Playlists] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun playlists() = Playlists { file }

    private fun Playlists.loaded() = runBlocking { load() }

    private fun Playlists.created(name: String, song: Song? = null) = runBlocking { create(name, song) }

    private fun Playlists.added(playlistId: Long, songId: Long) = runBlocking { add(playlistId, songs.getValue(songId)) }

    private fun Playlists.entriesOf(playlistId: Long) = all.value!!.single { it.id == playlistId }.entries

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
    fun anAddedSongGoesLastAndComesBackAfterARestart() {
        val two = entry(2)
        saved(1, "Road trip", two)
        val playlists = playlists()
        assertTrue(playlists.added(1, 4))
        assertEquals(listOf(2L, 4L), playlists.all.value!!.single().songIds())
        assertEquals(listOf(2L, 4L), playlists().loaded().single().songIds())
        assertEquals(songs.getValue(4).durationMs, playlists.entriesOf(1).last().durationMs)
    }

    /** As decided: adding a song that's there already adds it again, as an entry of its own. */
    @Test
    fun aSongAddedAgainIsThereTwice() {
        val playlists = playlists()
        val id = playlists.created("Road trip")
        playlists.added(id, 3)
        playlists.added(id, 3)
        val entries = playlists.entriesOf(id)
        assertEquals(listOf(3L, 3L), entries.map { it.songId })
        assertEquals(2, entries.map { it.id }.distinct().size)
        assertEquals(2, playlists.all.value!!.single().timesOf(songs.getValue(3)))
    }

    /** E.g. New playlist on the list of playlists a song is added to. */
    @Test
    fun aNewOneCanStartWithASong() {
        val playlists = playlists()
        val id = playlists.created("Road trip", songs.getValue(2))
        assertEquals(listOf(2L), playlists.all.value!!.single().songIds())
        assertEquals(listOf(2L), playlists().loaded().single { it.id == id }.songIds())
    }

    @Test
    fun addingToAPlaylistThatIsntThereDoesNothing() {
        saved(1, "Road trip")
        val playlists = playlists()
        playlists.loaded()
        val writes = file.writes
        assertFalse(playlists.added(2, 3))
        assertEquals(writes, file.writes)
        assertEquals(emptyList<PlaylistEntry>(), playlists.entriesOf(1))
    }

    @Test
    fun entriesInEveryPlaylistGetIdsOfTheirOwn() {
        val playlists = playlists()
        val road = playlists.created("Road trip", songs.getValue(1))
        val workout = playlists.created("Workout", songs.getValue(1))
        playlists.added(road, 2)
        playlists.added(workout, 2)
        val ids = playlists.all.value!!.flatMap { playlist -> playlist.entries.map { it.id } }
        assertEquals(4, ids.distinct().size)
    }

    /** E.g. the first of a song that's there twice: the second stays, and so does the rest. */
    @Test
    fun removingTakesOutThatOneEntry() {
        val first = entry(3)
        val one = entry(1)
        val second = entry(3)
        saved(1, "Road trip", first, one, second)
        saved(2, "Workout", entry(3))
        val playlists = playlists()
        playlists.loaded()
        val workout = playlists.entriesOf(2)
        playlists.remove(1, first.id, 3)
        assertEquals(listOf(one, second), playlists.entriesOf(1))
        assertEquals(workout, playlists.entriesOf(2))
        assertEquals(listOf(one, second), playlists().loaded().single { it.id == 1L }.entries)
    }

    /** The playlist changed since the song was held there: the entry is gone, or isn't that song's. */
    @Test
    fun removingAnEntryThatIsntThatSongsDoesNothing() {
        val three = entry(3)
        saved(1, "Road trip", three)
        val playlists = playlists()
        playlists.loaded()
        val writes = file.writes
        playlists.remove(1, three.id, 4)
        playlists.remove(1, three.id + 1, 3)
        playlists.remove(2, three.id, 3)
        assertEquals(listOf(three), playlists.entriesOf(1))
        assertEquals(writes, file.writes)
    }

    /**
     * The player's queue may still carry a removed entry, also after a restart: a new one never gets
     * its ID, not even when it was the newest.
     */
    @Test
    fun anEntryIdIsNeverGivenTwice() {
        val playlists = playlists()
        val id = playlists.created("Road trip", songs.getValue(1))
        playlists.added(id, 2)
        val newest = playlists.entriesOf(id).last()
        playlists.remove(id, newest.id, 2)
        val again = playlists()
        again.added(id, 2)
        assertTrue(again.entriesOf(id).last().id > newest.id)
    }

    /**
     * The same from data saved before Gusle kept the counter, where it's worked out from the entries:
     * the newest entry leaves (taken out, its song deleted, or another song's now), then Gusle starts
     * again. Each time, the next entry's ID is past the one that left.
     */
    @Test
    fun anEntryIdIsNeverGivenTwiceAfterDataFromBeforeTheCounter() {
        val leaving: List<(Playlists, PlaylistEntry) -> Unit> = listOf(
            { playlists, entry -> playlists.remove(1, entry.id, entry.songId) },
            { playlists, entry -> playlists.forget(entry.songId) },
            { playlists, entry -> playlists.matchWith(songs + (entry.songId to song(entry.songId, durationMs = 95_000))) },
        )
        for (leave in leaving) {
            file.values.clear()
            val first = entry(2)
            val newest = entry(4)
            saved(1, "Road trip", first, newest)
            val playlists = playlists()
            playlists.loaded()
            leave(playlists, newest)
            assertEquals(listOf(2L), playlists.entriesOf(1).map { it.songId })
            val again = playlists()
            again.added(1, 3)
            assertTrue(again.entriesOf(1).last().id > newest.id)
        }
    }

    /** E.g. on a song's actions Gusle was restored with, before the playlists are read. */
    @Test
    fun anEntryTakenOutBeforeTheReadIsGoneOnceRead() {
        val two = entry(2)
        val three = entry(3)
        saved(1, "Road trip", two, three)
        val playlists = playlists()
        playlists.remove(1, two.id, 2)
        assertEquals(listOf(three), playlists.loaded().single().entries)
        assertEquals(listOf(three), playlists().loaded().single().entries)
    }

    /** E.g. entries written before Gusle kept the counter: new ones come after them. */
    @Test
    fun newEntryIdsComeAfterTheOnesRead() {
        val entries = arrayOf(entry(1), entry(2))
        saved(1, "Road trip", *entries)
        val playlists = playlists()
        playlists.added(1, 3)
        assertEquals(entries.last().id + 1, playlists.entriesOf(1).last().id)
    }

    @Test
    fun aSongIsInAPlaylistAsOftenAsItWasAdded() {
        saved(1, "Road trip", entry(2), entry(3), entry(2))
        val playlist = playlists().loaded().single()
        assertEquals(2, playlist.timesOf(songs.getValue(2)))
        assertEquals(1, playlist.timesOf(songs.getValue(3)))
        assertEquals(0, playlist.timesOf(songs.getValue(4)))
        // Another song with song 2's ID isn't in it.
        assertEquals(0, playlist.timesOf(song(2, durationMs = 95_000)))
    }

    @Test
    fun aRenamedOneKeepsItsPlaceAndSongsAndComesBackAfterARestart() {
        val two = entry(2)
        saved(1, "Road trip", two)
        saved(2, "Workout")
        val playlists = playlists()
        assertTrue(runBlocking { playlists.rename(1, "Night drive") })
        val expected = listOf(Playlist(1, "Night drive", listOf(two)), Playlist(2, "Workout", emptyList()))
        assertEquals(expected, playlists.all.value)
        assertEquals(expected, playlists().loaded())
    }

    /** E.g. the current name, offered as the text input's choice, was tapped. */
    @Test
    fun renamingToTheSameNameWritesNothing() {
        saved(1, "Road trip")
        val playlists = playlists()
        playlists.loaded()
        val writes = file.writes
        assertTrue(runBlocking { playlists.rename(1, "Road trip") })
        assertEquals(writes, file.writes)
    }

    @Test
    fun renamingOrDeletingOneThatIsntThereDoesNothing() {
        saved(1, "Road trip")
        val playlists = playlists()
        playlists.loaded()
        val writes = file.writes
        assertFalse(runBlocking { playlists.rename(2, "Workout") })
        assertFalse(runBlocking { playlists.delete(2) })
        assertEquals(writes, file.writes)
        assertEquals(listOf(Playlist(1, "Road trip", emptyList())), playlists.all.value)
    }

    @Test
    fun aDeletedOneIsGoneAfterARestartAndTheOthersStay() {
        val two = entry(2)
        saved(1, "Road trip", entry(3))
        saved(2, "Workout", two)
        val playlists = playlists()
        assertTrue(runBlocking { playlists.delete(1) })
        val expected = listOf(Playlist(2, "Workout", listOf(two)))
        assertEquals(expected, playlists.all.value)
        assertEquals(expected, playlists().loaded())
    }

    /**
     * The player's queue may still carry a deleted playlist's ID and its entries', also after a
     * restart: a new one never gets them, not even when they were the newest.
     */
    @Test
    fun aDeletedOnesIdsAreNeverGivenAgain() {
        val playlists = playlists()
        playlists.created("Road trip", songs.getValue(1))
        val newest = playlists.created("Workout", songs.getValue(2))
        val newestEntry = playlists.entriesOf(newest).single()
        runBlocking { playlists.delete(newest) }
        val again = playlists()
        val next = again.created("Calm", songs.getValue(3))
        assertTrue(next > newest)
        assertTrue(again.entriesOf(next).single().id > newestEntry.id)
    }

    /** Where nothing has counted the IDs yet: from the highest left, the deleted one's would be next. */
    @Test
    fun aDeletedOnesIdsAreNeverGivenAgainAfterDataFromBeforeTheCounters() {
        saved(1, "Road trip", entry(1))
        val newestEntry = entry(2)
        saved(2, "Workout", newestEntry)
        runBlocking { playlists().delete(2) }
        val again = playlists()
        val next = again.created("Calm", songs.getValue(3))
        assertTrue(next > 2)
        assertTrue(again.entriesOf(next).single().id > newestEntry.id)
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
