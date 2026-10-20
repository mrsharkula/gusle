package com.sharkula.gusle.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSourceTest {
    @Test
    fun readsBackWhatWasSaved() {
        val sources = listOf(
            QueueSource.AllSongs,
            QueueSource.Artist("sabaton"),
            QueueSource.Artist(null),
            QueueSource.Album("three days grace\none-x"),
            QueueSource.Album(null),
            QueueSource.Favorites,
        )
        for (source in sources) {
            assertEquals(source, QueueSource.decode(source.encoded))
        }
        // An artist's key is as it was, whatever's in it.
        assertEquals(QueueSource.Artist("ac:dc "), QueueSource.decode(QueueSource.Artist("ac:dc ").encoded))
    }

    /** As in a queue saved before Gusle kept where its songs came from. */
    @Test
    fun allSongsIsSavedAsNothing() {
        assertNull(QueueSource.AllSongs.encoded)
    }

    /** E.g. the name an earlier build saved, rather than what the source is. */
    @Test
    fun anythingElseIsAllSongs() {
        assertEquals(QueueSource.AllSongs, QueueSource.decode("Test Trio"))
        assertEquals(QueueSource.AllSongs, QueueSource.decode(""))
    }

    /** E.g. the artist Three Days Grace and their album "Three Days Grace" on the watch. */
    @Test
    fun keepsAnArtistAndAnAlbumOfTheSameNameApart() {
        val artist = QueueSource.Artist("three days grace")
        val album = QueueSource.Album("three days grace\nthree days grace")
        assertNotEquals(artist.encoded, album.encoded)
        assertEquals(album, QueueSource.decode(album.encoded))
    }

    /** An artist or album named "favorites" isn't the favorite songs. */
    @Test
    fun keepsTheFavoritesApartFromAnArtistOrAlbumOfThatName() {
        for (named in listOf(QueueSource.Artist("favorites"), QueueSource.Album("favorites\nfavorites"))) {
            assertNotEquals(QueueSource.Favorites.encoded, named.encoded)
            assertEquals(named, QueueSource.decode(named.encoded))
        }
    }

    @Test
    fun keepsTheSongsWithoutAnArtistApartFromThoseWithoutAnAlbum() {
        assertNotEquals(QueueSource.Artist(null).encoded, QueueSource.Album(null).encoded)
    }

    /** An artist literally tagged "Unknown artist" isn't the songs without an artist. */
    @Test
    fun keepsAnArtistNamedLikeTheSongsWithoutOneApart() {
        val named = QueueSource.Artist("unknown artist")
        val none = QueueSource.Artist(null)
        assertEquals(named, QueueSource.decode(named.encoded))
        assertEquals(none, QueueSource.decode(none.encoded))
        assertNotEquals(named.encoded, none.encoded)
    }
}
