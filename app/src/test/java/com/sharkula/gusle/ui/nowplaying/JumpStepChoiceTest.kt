package com.sharkula.gusle.ui.nowplaying

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What's the step's own; the rest is SavedChoiceTest's. */
class JumpStepChoiceTest {
    /** The file on "disk"; a new [JumpStepChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun jumpStepChoice() = JumpStepChoice { file }

    private fun JumpStepChoice.loaded() = runBlocking { load() }

    @Test
    fun isTheDefaultBeforeAnyIsChosen() {
        assertEquals(JumpStep.Default, jumpStepChoice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        jumpStepChoice().choose(JumpStep.Fifteen)
        assertEquals("15", file.values["step"])
        assertEquals(JumpStep.Fifteen, jumpStepChoice().loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        // E.g. a step a later build no longer has.
        file.values["step"] = "30"
        assertEquals(JumpStep.Default, jumpStepChoice().loaded())
    }
}
