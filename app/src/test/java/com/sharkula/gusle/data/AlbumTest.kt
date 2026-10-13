package com.sharkula.gusle.data

import android.net.TestUri
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTest {
    private val collator = Collator.getInstance(Locale.ENGLISH)
    private var lastId = 0L

    /** A song, made in the order the library lists them: A–Z by title. */
    private fun song(
        title: String,
        album: String?,
        artist: String? = "Test Trio",
        albumArtist: String? = null,
        track: Int? = null,
    ) = Song(++lastId, TestUri, title, artist, 0, 0, album, albumArtist, track)

    private fun albums(vararg songs: Song): List<Album> {
        val list = songs.toList()
        return groupByAlbum(list, groupByArtist(list, collator), collator)
    }

    @Test
    fun putsTheSongsInDiscAndTrackOrderThenTheRestAToZ() {
        val album = albums(
            song("A", "Live", track = 2001),
            song("B", "Live"),
            song("C", "Live", track = 1002),
            // No disc number: on disc 1.
            song("D", "Live", track = 3),
            song("E", "Live", track = 1001),
            song("F", "Live"),
        ).single()
        assertEquals(listOf("E", "C", "D", "A", "B", "F"), album.songs.map { it.title })
    }

    @Test
    fun sortsTheAlbumsWithTheCollatorThenByArtist() {
        val albums = albums(
            song("A", "Zora"),
            song("B", "beta"),
            song("C", "Greatest Hits", albumArtist = "Test Trio"),
            song("D", "Greatest Hits", artist = "Test Choir", albumArtist = "Test Choir"),
        )
        assertEquals(listOf("beta", "Greatest Hits", "Greatest Hits", "Zora"), albums.map { it.name })
        assertEquals(listOf("Test Choir", "Test Trio"), albums.filter { it.name == "Greatest Hits" }.map { it.artist })
    }

    /** All the songs on the watch are in one folder, where MediaStore's album ID would make these one. */
    @Test
    fun keepsTwoArtistsAlbumsOfTheSameNameApartAlsoWithoutAlbumArtists() {
        val albums = albums(song("A", "Greatest Hits", artist = "ABBA"), song("B", "Greatest Hits", artist = "sabaton"))
        assertEquals(listOf("ABBA", "sabaton"), albums.map { it.artist })
        assertTrue(albums.none { it.variousArtists })
    }

    @Test
    fun takesAlbumArtistsThatDifferOnlyInCaseOrSpacesForOneAlbum() {
        val album = albums(
            song("A", "Trio Live", albumArtist = "Test Trio"),
            song("B", "trio live ", albumArtist = "Test trio "),
            // No album artist, but the album's artist.
            song("C", "Trio Live"),
        ).single()
        assertEquals("Trio Live", album.name)
        assertEquals(3, album.songs.size)
    }

    @Test
    fun spellsItsArtistAsArtistsDoes() {
        val albums = albums(
            song("A", "Heroes", artist = "sabaton"),
            song("B", "Heroes", artist = "sabaton"),
            song("C", "Primo Victoria", artist = "Sabaton"),
            song("D", "The Art of War", artist = "Sabaton"),
            song("E", "The Last Stand", artist = "Sabaton"),
        )
        assertEquals(listOf("Sabaton"), albums.map { it.artist }.distinct())
    }

    @Test
    fun namesAnAlbumArtistWithoutSongsOfTheirOwnAsTheTagsDo() {
        val album = albums(
            song("A", "Hits", artist = "Test Trio", albumArtist = "Various Artists"),
            song("B", "Hits", artist = "Test Choir", albumArtist = "Various Artists"),
        ).single()
        assertEquals("Various Artists", album.artist)
        assertFalse(album.variousArtists)
    }

    @Test
    fun putsTheSongsWithoutAnAlbumLastAsOne() {
        val albums = albums(song("A", null), song("B", "Zora"), song("C", null))
        assertEquals(listOf("Zora", null), albums.map { it.name })
        assertNull(albums.last().key)
        assertEquals(listOf("A", "C"), albums.last().songs.map { it.title })
    }

    @Test
    fun namesTheSongsWithoutAnAlbumAfterTheirArtistsOnly() {
        // A few album artists say nothing about the rest; songs without an artist don't count either.
        val shared = albums(
            song("A", null, albumArtist = "Test Choir"),
            song("B", null),
            song("C", null, artist = null),
        ).single()
        assertEquals("Test Trio", shared.artist)
        assertFalse(shared.variousArtists)
        val various = albums(song("A", null), song("B", null, artist = "Test Choir")).single()
        assertNull(various.artist)
        assertTrue(various.variousArtists)
        val none = albums(song("A", null, artist = null)).single()
        assertNull(none.artist)
        assertFalse(none.variousArtists)
    }

    @Test
    fun namesAnAlbumAsMostOfItsSongsSpellIt() {
        val album = albums(song("A", "heroes"), song("B", "Heroes"), song("C", "Heroes ")).single()
        assertEquals("Heroes", album.name)
    }

    @Test
    fun knowsAnAlbumByItsNameAndArtist() {
        assertNull(albumKey(song("A", null)))
        val key = albumKey(song("A", " Three  Days Grace", artist = "Three Days Grace"))!!
        assertEquals("three days grace\nthree days grace", key)
        assertEquals("three days grace", albumNameIn(key))
    }
}
