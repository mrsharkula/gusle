package com.sharkula.gusle.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSourceTest {
    @Test
    fun readsBackWhatWasSaved() {
        for (source in listOf(QueueSource.AllSongs, QueueSource.Artist("sabaton"), QueueSource.Artist(null))) {
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
