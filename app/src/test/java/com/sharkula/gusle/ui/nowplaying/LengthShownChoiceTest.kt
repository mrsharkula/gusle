package com.sharkula.gusle.ui.nowplaying

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What's this choice's own; the rest is SavedChoiceTest's. */
class LengthShownChoiceTest {
    /** The file on "disk"; a new [LengthShownChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun lengthShownChoice() = LengthShownChoice { file }

    private fun LengthShownChoice.loaded() = runBlocking { load() }

    @Test
    fun isTheLengthBeforeAnyTap() {
        assertEquals(LengthShown.Length, lengthShownChoice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        lengthShownChoice().choose(LengthShown.TimeLeft)
        // Under its own key, next to the steps' ("step", "crown").
        assertEquals("left", file.values["length"])
        assertEquals(LengthShown.TimeLeft, lengthShownChoice().loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        file.values["length"] = "remaining"
        assertEquals(LengthShown.Default, lengthShownChoice().loaded())
    }
}
