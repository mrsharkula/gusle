package com.sharkula.gusle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingKeyTest {
    /** The file on "disk"; a new [NowPlayingKey] on it is Gusle started again. */
    private val prefs = FakePrefs()

    private fun key() = NowPlayingKey { prefs }

    @Test
    fun staysTheSameAfterARestart() {
        assertEquals(key().value(), key().value())
    }

    @Test
    fun matchesOnlyItself() {
        val value = key().value()
        assertTrue(key().matches(value))
        assertFalse(key().matches(value.dropLast(1)))
        assertFalse(key().matches(""))
        assertFalse(key().matches("true"))
    }

    /** Nothing an app could send matches before the tile has made the key, nor makes one. */
    @Test
    fun matchesNothingBeforeItsMade() {
        assertFalse(key().matches(""))
        assertTrue(prefs.values.isEmpty())
    }

    @Test
    fun isDifferentOnEveryWatch() {
        assertNotEquals(key().value(), NowPlayingKey { FakePrefs() }.value())
    }
}
