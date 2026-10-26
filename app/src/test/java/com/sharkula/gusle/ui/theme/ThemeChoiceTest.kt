package com.sharkula.gusle.ui.theme

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What's the theme's own; the rest is SavedChoiceTest's. */
class ThemeChoiceTest {
    /** The file on "disk"; a new [ThemeChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun themeChoice() = ThemeChoice { file }

    private fun ThemeChoice.loaded() = runBlocking { load() }

    @Test
    fun isTheDefaultBeforeAnyIsChosen() {
        assertEquals(AppTheme.Default, themeChoice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        themeChoice().choose(AppTheme.Adriatic)
        assertEquals("adriatic", file.values["theme"])
        assertEquals(AppTheme.Adriatic, themeChoice().loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        // E.g. a theme a later build no longer has.
        file.values["theme"] = "sand"
        assertEquals(AppTheme.Default, themeChoice().loaded())
    }
}
