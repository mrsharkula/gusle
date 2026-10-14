package com.sharkula.gusle.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekStepsTest {
    private val song = 225_000L // 3:45

    @Test
    fun slowStepsMoveFiveSecondsEach() {
        val steps = SeekSteps()
        assertEquals(88_000, steps.next(83_000, forward = true, timeMs = 1_000, song))
        assertEquals(93_000, steps.next(88_000, forward = true, timeMs = 1_200, song))
        assertEquals(88_000, steps.next(93_000, forward = false, timeMs = 1_400, song))
    }

    @Test
    fun quickStepsMoveFurther() {
        assertEquals(1, stepsWorth(200))
        assertEquals(1, stepsWorth(80))
        assertEquals(2, stepsWorth(79))
        assertEquals(2, stepsWorth(40))
        assertEquals(4, stepsWorth(39))
        // Never more, however quick.
        assertEquals(4, stepsWorth(0))
    }

    /** Steps the crown reports at once go as far as the first of them, not four times as far. */
    @Test
    fun stepsThatComeTogetherGoAlike() {
        val steps = SeekSteps()
        steps.next(78_000, forward = true, timeMs = 500, song)
        // Two at once, half a second on: slow, 5 s each.
        assertEquals(88_000, steps.next(83_000, forward = true, timeMs = 1_000, song))
        assertEquals(93_000, steps.next(88_000, forward = true, timeMs = 1_000, song))
        // Two at once right after: quick, 20 s each.
        assertEquals(113_000, steps.next(93_000, forward = true, timeMs = 1_030, song))
        assertEquals(133_000, steps.next(113_000, forward = true, timeMs = 1_030, song))
    }

    @Test
    fun theFirstStepIsAlwaysOne() {
        assertEquals(88_000, SeekSteps().next(83_000, forward = true, timeMs = 10, song))
    }

    @Test
    fun turningBackStartsSlowlyAgain() {
        val steps = SeekSteps()
        steps.next(83_000, forward = true, timeMs = 1_000, song)
        assertEquals(108_000, steps.next(88_000, forward = true, timeMs = 1_030, song))
        assertEquals(103_000, steps.next(108_000, forward = false, timeMs = 1_060, song))
        assertEquals(83_000, steps.next(103_000, forward = false, timeMs = 1_090, song))
    }

    /** A quick spin of the crown goes through most of a 4-minute song, so two cover it all. */
    @Test
    fun aQuickTurnCoversHalfASong() {
        val steps = SeekSteps()
        var at = 0L
        // About a quarter turn, quickly: 8 steps 30 ms apart.
        repeat(8) { at = steps.next(at, forward = true, timeMs = 1_000L + it * 30, durationMs = 240_000) }
        assertTrue("$at", at >= 120_000)
    }

    @Test
    fun staysWithinTheSong() {
        val steps = SeekSteps()
        assertEquals(0, steps.next(3_000, forward = false, timeMs = 1_000, song))
        assertEquals(0, steps.next(0, forward = false, timeMs = 1_500, song))
        assertEquals(song, steps.next(222_000, forward = true, timeMs = 2_000, song))
        assertEquals(song, steps.next(song, forward = true, timeMs = 2_500, song))
    }

    @Test
    fun aSongOfUnknownLengthStaysAtTheStart() {
        assertEquals(0, SeekSteps().next(0, forward = true, timeMs = 1_000, durationMs = 0))
    }
}
