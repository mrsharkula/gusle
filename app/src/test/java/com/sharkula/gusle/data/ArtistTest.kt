package com.sharkula.gusle.data

import android.net.TestUri
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistTest {
    private val collator = Collator.getInstance(Locale.ENGLISH)
    private var lastId = 0L

    /** A song, made in the order the library lists them: A–Z by title. */
    private fun song(title: String, artist: String?) = Song(++lastId, TestUri, title, artist, 0, 0)

    private fun List<Artist>.names() = map { it.name }

    @Test
    fun sortsTheArtistsWithTheCollator() {
        val songs = listOf(song("A", "Zora"), song("B", "beta"), song("C", "Šuma"), song("D", "Alpha"))
        // By character code, it would be Alpha, Zora, beta, Šuma.
        assertEquals(listOf("Alpha", "beta", "Šuma", "Zora"), groupByArtist(songs, collator).names())
    }

    @Test
    fun putsTheSongsWithoutAnArtistLastAsOne() {
        val songs = listOf(song("A", null), song("B", "Zora"), song("C", null))
        val artists = groupByArtist(songs, collator)
        assertEquals(listOf("Zora", null), artists.names())
        assertEquals(listOf(songs[0], songs[2]), artists.last().songs)
        assertEquals(null, artists.last().key)
    }

    @Test
    fun keepsEachArtistsSongsInTheLibrarysOrder() {
        val songs = listOf(song("Apologize", "B"), song("Bismarck", "A"), song("Riot", "B"), song("Yamato", "A"))
        val artists = groupByArtist(songs, collator)
        assertEquals(listOf(songs[1], songs[3]), artists[0].songs)
        assertEquals(listOf(songs[0], songs[2]), artists[1].songs)
    }

    @Test
    fun takesTagsThatDifferOnlyInCaseOrSpacesForOneArtist() {
        val songs = listOf(
            song("A", "sabaton"),
            song("B", "Sabaton "),
            song("C", "SABATON"),
            song("D", " Three  Days Grace"),
            song("E", "sabaton"),
            // A no-break space between the words.
            song("F", "Three\u00A0Days Grace"),
        )
        val artists = groupByArtist(songs, collator)
        assertEquals(listOf("sabaton", "Three Days Grace"), artists.names())
        assertEquals(listOf(songs[0], songs[1], songs[2], songs[4]), artists[0].songs)
        assertEquals(listOf(songs[3], songs[5]), artists[1].songs)
        assertEquals(listOf("sabaton", "three days grace"), artists.map { it.key })
    }

    @Test
    fun namesAnArtistAsMostOfTheirSongsSpellIt() {
        val most = listOf(song("A", "sabaton"), song("B", "Sabaton"), song("C", "sabaton"))
        assertEquals(listOf("sabaton"), groupByArtist(most, collator).names())
        // As many each way: capitals first.
        val tied = listOf(song("A", "sabaton"), song("B", "Sabaton"))
        assertEquals(listOf("Sabaton"), groupByArtist(tied, collator).names())
    }

    @Test
    fun keepsFeaturedArtistsApart() {
        val songs = listOf(song("A", "Test Trio"), song("B", "Test Trio feat. Test Choir"), song("C", "Test Trio & Friends"))
        assertEquals(
            listOf("Test Trio", "Test Trio & Friends", "Test Trio feat. Test Choir"),
            groupByArtist(songs, collator).names(),
        )
    }

    /** So songs read again unchanged don't redraw anything. */
    @Test
    fun songsReadAgainUnchangedAreEqual() {
        val songs = listOf(song("A", "Zora"), song("B", null), song("C", "Alpha"))
        val again = songs.map { it.copy() }
        fun loaded(songs: List<Song>) = groupByArtist(songs, collator).let { artists ->
            Songs.Loaded(songs, artists, groupByAlbum(songs, artists, collator))
        }
        assertEquals(loaded(songs), loaded(again))
    }
}
