package com.sharkula.gusle.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class JumpStepTest {
    /** What a choice is saved as: changing one would lose it for whoever chose that step. */
    @Test
    fun ids() {
        assertEquals(listOf("5", "10", "15"), JumpStep.entries.map { it.id })
        assertEquals(JumpStep.Fifteen, JumpStep.fromId("15"))
        assertEquals(JumpStep.Default, JumpStep.fromId(null))
        assertEquals(JumpStep.Default, JumpStep.fromId("30"))
    }

    /** As the buttons always jumped, before there was a choice. */
    @Test
    fun tenSecondsIsTheDefault() {
        assertEquals(JumpStep.Ten, JumpStep.Default)
        assertEquals(10_000L, JumpStep.Default.ms)
    }

    @Test
    fun aJumpIsItsSeconds() {
        assertEquals(listOf(5_000L, 10_000L, 15_000L), JumpStep.entries.map { it.ms })
    }
}
