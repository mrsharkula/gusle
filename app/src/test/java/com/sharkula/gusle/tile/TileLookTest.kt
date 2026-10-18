package com.sharkula.gusle.tile

import android.net.TestUri
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playback.PlayerState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLookTest {
    private val yamato = Song(1, TestUri, "Yamato", "Sabaton", durationMs = 225_400, fileModified = 10)
    private val ghostDivision = Song(2, TestUri, "Ghost Division", "Sabaton", durationMs = 200_000, fileModified = 20)
    private val songs = Songs.Loaded(listOf(yamato, ghostDivision), emptyList(), emptyList())

    private val at = Instant.ofEpochMilli(1_000_000_000)

    private fun look(
        song: Song?,
        isPlaying: Boolean = true,
        positionMs: Long = 83_000,
        durationMs: Long? = null,
        at: Instant = this.at,
        songs: Songs = this.songs,
        themeId: String = "walnut",
    ): TileLook {
        val player = song?.let { PlayerState(it.id, isPlaying, positionMs, durationMs) }
        return TileLook.of(TileContent.of(player, songs, coverSizePx = 314, at), themeId)
    }

    /** What a tap on the tile showed, and the player a moment later, where it took a moment to start. */
    @Test
    fun aPlayingSongLooksTheSameWhileItsTimeIsNearlyTheSame() {
        val shown = look(yamato, positionMs = 93_000)
        assertTrue(shown.isLike(look(yamato, positionMs = 94_500, at = at.plusMillis(3_000))))
        assertTrue(shown.isLike(look(yamato, positionMs = 94_000)))
    }

    @Test
    fun aPlayingSongLooksDifferentOnceItsTimeIsOff() {
        val shown = look(yamato, positionMs = 93_000)
        assertFalse(shown.isLike(look(yamato, positionMs = 93_500, at = at.plusMillis(3_000))))
        assertFalse(shown.isLike(look(yamato, positionMs = 103_000)))
    }

    /** A jump to the end of a song: the tile showed it ended, and the player moved on to the next. */
    @Test
    fun theNextSongLooksDifferent() {
        val shown = look(yamato, positionMs = 225_400, durationMs = 225_400)
        assertFalse(shown.isLike(look(ghostDivision, positionMs = 300, at = at.plusMillis(3_000))))
    }

    @Test
    fun aPausedSongLooksTheSameAtTheSameSecond() {
        val shown = look(yamato, isPlaying = false, positionMs = 83_100)
        assertTrue(shown.isLike(look(yamato, isPlaying = false, positionMs = 83_900, at = at.plusMillis(60_000))))
        assertFalse(shown.isLike(look(yamato, isPlaying = false, positionMs = 84_000)))
    }

    @Test
    fun playingAndPausedLookDifferent() {
        assertFalse(look(yamato).isLike(look(yamato, isPlaying = false)))
    }

    /** The player knows the length a little more exactly than MediaStore did. */
    @Test
    fun theLengthCountsInWholeSeconds() {
        val shown = look(yamato)
        assertTrue(shown.isLike(look(yamato, durationMs = 225_412)))
        assertFalse(shown.isLike(look(yamato, durationMs = 226_000)))
    }

    @Test
    fun anotherThemeLooksDifferent() {
        assertFalse(look(yamato).isLike(look(yamato, themeId = "adriatic")))
        assertFalse(look(null).isLike(look(null, themeId = "adriatic")))
    }

    /** A cover that couldn't be read is left out, and tried again the next time. */
    @Test
    fun aSongWithoutItsCoverLooksDifferent() {
        val content = TileContent.of(PlayerState(yamato.id, true, 83_000, null), songs, 314, at)
        assertFalse(TileLook.of(content.withoutCover(), "walnut").isLike(TileLook.of(content, "walnut")))
    }

    @Test
    fun aSongGoneFromTheWatchIsNothingToPlay() {
        val withoutYamato = Songs.Loaded(listOf(ghostDivision), emptyList(), emptyList())
        assertEquals(look(null), look(yamato, songs = withoutYamato))
    }

    /** E.g. once the watch has indexed the songs after a restart. */
    @Test
    fun songsToShuffleLookDifferent() {
        assertFalse(look(null, songs = Songs.Loaded(emptyList(), emptyList(), emptyList())).isLike(look(null)))
        assertFalse(look(null, songs = Songs.Failed).isLike(look(null)))
    }

    @Test
    fun onlyAPlayingSongCounts() {
        assertEquals(at.toEpochMilli() - 83_000, look(yamato).countsFromMs)
        assertNull(look(yamato, isPlaying = false).countsFromMs)
        assertNull(look(null).countsFromMs)
    }

    /** Without its length, the tile can't count, so its time stands still. */
    @Test
    fun aSongOfUnknownLengthStandsStill() {
        val unknown = yamato.copy(durationMs = 0)
        val songs = Songs.Loaded(listOf(unknown), emptyList(), emptyList())
        assertNull(look(unknown, songs = songs).countsFromMs)
    }
}
