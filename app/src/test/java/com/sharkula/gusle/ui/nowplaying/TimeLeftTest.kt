package com.sharkula.gusle.ui.nowplaying

import com.sharkula.gusle.ui.components.formatLength
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLeftTest {
    /** 3:55.6 of 4:47.3: the big time shows 3:55, so 0:52 is left, not the 51.7 s between them. */
    @Test
    fun inWholeSecondsThatAddUpToTheLength() {
        assertEquals(52_000L, timeLeftMs(287_300, 235_600))
        assertEquals("0:52", formatLength(timeLeftMs(287_300, 235_600)))
    }

    @Test
    fun theWholeSongAtTheStartAndNothingAtTheEnd() {
        assertEquals(287_000L, timeLeftMs(287_300, 0))
        assertEquals(0L, timeLeftMs(287_300, 287_300))
    }

    /** Past the end, e.g. a position from the song before: nothing left, never a negative time. */
    @Test
    fun neverBelowZero() {
        assertEquals(0L, timeLeftMs(287_000, 300_000))
    }
}
