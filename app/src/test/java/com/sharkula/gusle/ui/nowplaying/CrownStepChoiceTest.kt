package com.sharkula.gusle.ui.nowplaying

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What's the step's own; the rest is SavedChoiceTest's. */
class CrownStepChoiceTest {
    /** The file on "disk"; a new [CrownStepChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun crownStepChoice() = CrownStepChoice { file }

    private fun CrownStepChoice.loaded() = runBlocking { load() }

    @Test
    fun isTheDefaultBeforeAnyIsChosen() {
        assertEquals(CrownStep.Default, crownStepChoice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        crownStepChoice().choose(CrownStep.Seven)
        // Under its own key, next to the jump step's ("step").
        assertEquals("7", file.values["crown"])
        assertEquals(CrownStep.Seven, crownStepChoice().loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        // E.g. a step a later build no longer has: 5, from the first build with the choice.
        file.values["crown"] = "5"
        assertEquals(CrownStep.Default, crownStepChoice().loaded())
    }
}
