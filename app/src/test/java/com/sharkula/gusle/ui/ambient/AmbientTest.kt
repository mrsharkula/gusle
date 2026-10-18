package com.sharkula.gusle.ui.ambient

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientTest {
    @Test
    fun minutesLeftRounded() {
        // Yamato at 1:23 of 3:45: 2:22 left.
        assertEquals(2, minutesLeft(83_000, 225_000))
        // A minute later: 1:22 left.
        assertEquals(1, minutesLeft(143_000, 225_000))
        assertEquals(3, minutesLeft(0, 150_000))
        assertEquals(2, minutesLeft(0, 149_999))
    }

    @Test
    fun atLeastAMinuteLeft() {
        assertEquals(1, minutesLeft(220_000, 225_000))
        assertEquals(1, minutesLeft(225_000, 225_000))
        // Past the end, e.g. a length MediaStore had wrong.
        assertEquals(1, minutesLeft(300_000, 225_000))
    }

    @Test
    fun burnInShiftsGoRoundAFewPixelsApart() {
        val shifts = (0 until 8).map(::burnInShift)
        assertEquals(DpOffset(0.dp, 0.dp), shifts[0])
        assertEquals(8, shifts.toSet().size)
        assertTrue(shifts.all { it.x.value in -2.5f..2.5f && it.y.value in -2.5f..2.5f })
        assertEquals(shifts[0], burnInShift(8))
        assertEquals(shifts[1], burnInShift(9))
    }
}
