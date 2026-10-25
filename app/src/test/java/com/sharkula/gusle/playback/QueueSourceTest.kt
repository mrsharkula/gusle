package com.sharkula.gusle.playback

import com.sharkula.gusle.data.Playlist
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
            QueueSource.Playlist(7, "Road trip"),
            // Whatever a playlist's name holds.
            QueueSource.Playlist(12, "playlist:3: x\ny"),
            QueueSource.Playlist(13, ""),
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

    /** An artist or album named like a playlist isn't one, nor a playlist named "favorites" the favorites. */
    @Test
    fun keepsPlaylistsApartFromWhatsNamedLikeThem() {
        val playlist = QueueSource.Playlist(3, "favorites")
        for (other in listOf(QueueSource.Artist("playlist:3:favorites"), QueueSource.Album("playlist:3\nfavorites"), QueueSource.Favorites)) {
            assertNotEquals(playlist.encoded, other.encoded)
            assertEquals(other, QueueSource.decode(other.encoded))
        }
        assertEquals(playlist, QueueSource.decode(playlist.encoded))
    }

    @Test
    fun aDamagedPlaylistIsAllSongs() {
        assertEquals(QueueSource.AllSongs, QueueSource.decode("playlist:"))
        assertEquals(QueueSource.AllSongs, QueueSource.decode("playlist:road trip"))
    }

    /** E.g. once it's renamed. */
    @Test
    fun aPlaylistGoesByTheNameItHasNow() {
        val playlists = listOf(Playlist(2, "Workout", emptyList()), Playlist(7, "Long road trip", emptyList()))
        assertEquals("Long road trip", QueueSource.Playlist(7, "Road trip").nameAmong(playlists))
    }

    @Test
    fun aDeletedPlaylistGoesByTheNameItHad() {
        val playlists = listOf(Playlist(2, "Workout", emptyList()))
        assertEquals("Road trip", QueueSource.Playlist(7, "Road trip").nameAmong(playlists))
        // Nor are they read yet, as the playback service starts.
        assertEquals("Road trip", QueueSource.Playlist(7, "Road trip").nameAmong(null))
    }

    /** Renamed, then deleted: the queue keeps the new name. */
    @Test
    fun aPlaylistKeepsTheNameItHasNow() {
        val renamed = QueueSource.Playlist(7, "Road trip").namedAmong(listOf(Playlist(7, "Night drive", emptyList())))
        assertEquals(QueueSource.Playlist(7, "Night drive"), renamed)
        assertEquals("Night drive", renamed.namedAmong(emptyList()).let { (it as QueueSource.Playlist).name })
        assertEquals(renamed, renamed.namedAmong(null))
        // Nothing else has a name to keep.
        val artist = QueueSource.Artist("sabaton")
        assertEquals(artist, artist.namedAmong(listOf(Playlist(7, "Night drive", emptyList()))))
    }
}
