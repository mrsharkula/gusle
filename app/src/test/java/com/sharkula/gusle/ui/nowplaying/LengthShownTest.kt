package com.sharkula.gusle.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class LengthShownTest {
    /** What a choice is saved as: changing one would lose it for whoever tapped the time. */
    @Test
    fun ids() {
        assertEquals(listOf("length", "left"), LengthShown.entries.map { it.id })
        assertEquals(LengthShown.TimeLeft, LengthShown.fromId("left"))
        assertEquals(LengthShown.Default, LengthShown.fromId(null))
        assertEquals(LengthShown.Default, LengthShown.fromId("remaining"))
    }

    /** As the screen always showed it, before there was a tap. */
    @Test
    fun theLengthIsTheDefault() {
        assertEquals(LengthShown.Length, LengthShown.Default)
    }

    @Test
    fun aTapShowsTheOther() {
        assertEquals(LengthShown.TimeLeft, LengthShown.Length.other)
        assertEquals(LengthShown.Length, LengthShown.TimeLeft.other)
    }
}
