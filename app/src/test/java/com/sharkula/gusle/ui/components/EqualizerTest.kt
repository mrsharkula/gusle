package com.sharkula.gusle.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerTest {
    /** Where they stand still, so they start moving without a jump. */
    @Test
    fun setOffFromTheStillHeights() {
        assertEquals(0.5f, barHeight(0, 0), 0.001f)
        assertEquals(1f, barHeight(1, 0), 0.001f)
        assertEquals(0.7f, barHeight(2, 0), 0.001f)
    }

    /** The middle bar starts at the top and takes 0.7 s each way, easing in and out. */
    @Test
    fun goDownAndUpAgain() {
        assertEquals(0.625f, barHeight(1, ms(350)), 0.001f)
        assertEquals(0.25f, barHeight(1, ms(700)), 0.001f)
        assertEquals(0.625f, barHeight(1, ms(1050)), 0.001f)
        assertEquals(1f, barHeight(1, ms(1400)), 0.001f)
    }

    @Test
    fun theOthersGoUpFirst() {
        assertTrue(barHeight(0, ms(50)) > 0.5f)
        assertTrue(barHeight(2, ms(50)) > 0.7f)
    }

    @Test
    fun stayBetweenAQuarterAndTheTop() {
        for (bar in 0..2) {
            for (step in 0..5000) {
                val height = barHeight(bar, ms(step * 7L))
                assertTrue("bar $bar at ${step * 7} ms: $height", height in 0.25f..1f)
            }
        }
    }

    /** Back where they were after going up and down once: 1.8 s, 1.4 s and 2.2 s. */
    @Test
    fun repeat() {
        for ((bar, round) in listOf(0 to 1800L, 1 to 1400L, 2 to 2200L)) {
            for (at in listOf(0L, 123L, 456L)) {
                assertEquals(barHeight(bar, ms(at)), barHeight(bar, ms(at + round)), 0.0001f)
            }
        }
    }

    private fun ms(ms: Long) = ms * 1_000_000
}
