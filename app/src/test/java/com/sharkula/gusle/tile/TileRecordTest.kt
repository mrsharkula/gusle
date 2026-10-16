package com.sharkula.gusle.tile

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileRecordTest {
    /** The file on "disk"; a new [TileRecord] on it is Gusle started again. */
    private val prefs = FakePrefs()

    private fun record() = TileRecord { prefs }

    private val playing = TileLook("song\u001FYamato", countsFromMs = 1_000_000)
    private val paused = TileLook("song\u001FYamato\u001F83", countsFromMs = null)

    @Test
    fun knowsNoTilesBeforeAnyIsRecorded() = runBlocking {
        assertNull(record().tileIds())
        assertNull(record().look())
    }

    @Test
    fun keepsTheTilesAfterARestart() = runBlocking {
        record().apply {
            added(7)
            added(12)
            removed(7)
        }
        assertEquals(setOf(12), record().tileIds())
    }

    /** Known not to be there, which is different from not known. */
    @Test
    fun keepsThatThereAreNone() = runBlocking {
        record().apply {
            added(7)
            removed(7)
        }
        assertEquals(emptySet<Int>(), record().tileIds())
    }

    @Test
    fun writesATileOnlyOnce() = runBlocking {
        record().apply {
            added(7)
            added(7)
        }
        assertEquals(1, prefs.writes)
    }

    @Test
    fun takesTheSystemsTilesOnlyWhenItKnowsNone() = runBlocking {
        record().apply {
            foundTiles(setOf(3))
            foundTiles(emptySet())
        }
        assertEquals(setOf(3), record().tileIds())
    }

    @Test
    fun keepsHowTheTileLookedAfterARestart() = runBlocking {
        record().shown(playing)
        assertEquals(playing, record().look())
        record().shown(paused)
        assertEquals(paused, record().look())
    }
}
