package com.sharkula.gusle.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class CrownStepTest {
    /** What a choice is saved as: changing one would lose it for whoever chose that step. */
    @Test
    fun ids() {
        assertEquals(listOf("1", "4", "7"), CrownStep.entries.map { it.id })
        assertEquals(CrownStep.Seven, CrownStep.fromId("7"))
        assertEquals(CrownStep.Default, CrownStep.fromId(null))
        // The steps of an earlier build (2, 5 and 10) are no more.
        assertEquals(CrownStep.Default, CrownStep.fromId("5"))
    }

    /** The middle one, nearest the 5 s the crown moved before there was a choice. */
    @Test
    fun fourSecondsIsTheDefault() {
        assertEquals(CrownStep.Four, CrownStep.Default)
        assertEquals(4_000L, CrownStep.Default.ms)
    }

    @Test
    fun aStepIsItsSeconds() {
        assertEquals(listOf(1_000L, 4_000L, 7_000L), CrownStep.entries.map { it.ms })
    }
}
