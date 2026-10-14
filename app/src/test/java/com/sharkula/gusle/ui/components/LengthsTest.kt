package com.sharkula.gusle.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LengthsTest {
    @Test
    fun minutesAndSeconds() {
        assertEquals("0:00", formatLength(0))
        assertEquals("0:05", formatLength(5_999))
        assertEquals("1:23", formatLength(83_000))
        assertEquals("59:59", formatLength(3_599_000))
    }

    @Test
    fun hoursFromAnHourOn() {
        assertEquals("1:00:00", formatLength(3_600_000))
        assertEquals("1:02:05", formatLength(3_725_000))
    }

    @Test
    fun inWords() {
        assertEquals("1 minute 23 seconds", words(83_000))
        assertEquals("3 minutes 45 seconds", words(225_400))
        assertEquals("1 hour 2 minutes 5 seconds", words(3_725_000))
    }

    /** As shown: "1:23" is 1 minute 23 seconds even at 1:23.9. */
    @Test
    fun inWholeSecondsLikeTheTime() {
        assertEquals("5 seconds", words(5_999))
    }

    @Test
    fun withoutThePartsThatAreZero() {
        assertEquals("45 seconds", words(45_000))
        assertEquals("2 minutes", words(120_000))
        assertEquals("1 hour", words(3_600_000))
        assertEquals("1 hour 5 seconds", words(3_605_000))
    }

    @Test
    fun zeroIsZeroSeconds() {
        assertEquals("0 seconds", words(0))
        assertEquals("0 seconds", words(999))
        assertEquals("0 seconds", words(-1_000))
    }

    /** English, as the app's strings have it. */
    private fun words(ms: Long) = lengthInWords(ms) { unit, count ->
        val name = when (unit) {
            LengthUnit.Hours -> "hour"
            LengthUnit.Minutes -> "minute"
            LengthUnit.Seconds -> "second"
        }
        if (count == 1) "$count $name" else "$count ${name}s"
    }
}
