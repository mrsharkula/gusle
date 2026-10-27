package com.sharkula.gusle.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** With the steps Settings offers: the default, 4 s, unless said otherwise. */
class SeekStepsTest {
    private val song = 225_000L // 3:45
    private val step = CrownStep.Default.ms // 4 s

    @Test
    fun slowStepsMoveTheStepEach() {
        val steps = SeekSteps(step)
        assertEquals(87_000, steps.next(83_000, forward = true, timeMs = 1_000, song))
        assertEquals(91_000, steps.next(87_000, forward = true, timeMs = 1_200, song))
        assertEquals(87_000, steps.next(91_000, forward = false, timeMs = 1_400, song))
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
        val steps = SeekSteps(step)
        steps.next(78_000, forward = true, timeMs = 500, song)
        // Two at once, half a second on: slow, 4 s each.
        assertEquals(87_000, steps.next(83_000, forward = true, timeMs = 1_000, song))
        assertEquals(91_000, steps.next(87_000, forward = true, timeMs = 1_000, song))
        // Two at once right after: quick, 16 s each.
        assertEquals(107_000, steps.next(91_000, forward = true, timeMs = 1_030, song))
        assertEquals(123_000, steps.next(107_000, forward = true, timeMs = 1_030, song))
    }

    @Test
    fun theFirstStepIsAlwaysOne() {
        assertEquals(87_000, SeekSteps(step).next(83_000, forward = true, timeMs = 10, song))
    }

    @Test
    fun turningBackStartsSlowlyAgain() {
        val steps = SeekSteps(step)
        steps.next(83_000, forward = true, timeMs = 1_000, song)
        assertEquals(103_000, steps.next(87_000, forward = true, timeMs = 1_030, song))
        assertEquals(99_000, steps.next(103_000, forward = false, timeMs = 1_060, song))
        assertEquals(83_000, steps.next(99_000, forward = false, timeMs = 1_090, song))
    }

    /** A quick spin of the crown goes through about half a 4-minute song, so a few cover it all. */
    @Test
    fun aQuickTurnCoversAboutHalfASong() {
        val steps = SeekSteps(step)
        var at = 0L
        // About a quarter turn, quickly: 8 steps 30 ms apart, 4 s then 16 s each.
        repeat(8) { at = steps.next(at, forward = true, timeMs = 1_000L + it * 30, durationMs = 240_000) }
        assertTrue("$at", at >= 100_000)
    }

    /** The other steps Settings offers; quick steps are multiples of them just the same. */
    @Test
    fun theOtherStepsMoveByThemselves() {
        val fine = SeekSteps(CrownStep.One.ms)
        assertEquals(84_000, fine.next(83_000, forward = true, timeMs = 1_000, song))
        assertEquals(86_000, fine.next(84_000, forward = true, timeMs = 1_050, song))
        assertEquals(90_000, fine.next(86_000, forward = true, timeMs = 1_080, song))
        assertEquals(90_000, SeekSteps(CrownStep.Seven.ms).next(83_000, forward = true, timeMs = 1_000, song))
    }

    @Test
    fun staysWithinTheSong() {
        val steps = SeekSteps(step)
        assertEquals(0, steps.next(3_000, forward = false, timeMs = 1_000, song))
        assertEquals(0, steps.next(0, forward = false, timeMs = 1_500, song))
        assertEquals(song, steps.next(222_000, forward = true, timeMs = 2_000, song))
        assertEquals(song, steps.next(song, forward = true, timeMs = 2_500, song))
    }

    @Test
    fun aSongOfUnknownLengthStaysAtTheStart() {
        assertEquals(0, SeekSteps(step).next(0, forward = true, timeMs = 1_000, durationMs = 0))
    }
}
