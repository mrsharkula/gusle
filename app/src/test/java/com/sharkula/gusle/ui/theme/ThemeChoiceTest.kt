package com.sharkula.gusle.ui.theme

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeChoiceTest {
    /** The file on "disk"; a new [ThemeChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun themeChoice() = ThemeChoice { file }

    private fun ThemeChoice.loaded() = runBlocking { load() }

    @Test
    fun isTheDefaultBeforeAnyIsChosen() {
        val themes = themeChoice()
        assertNull(themes.current)
        assertEquals(AppTheme.Default, themes.loaded())
        assertEquals(AppTheme.Default, themes.current)
    }

    @Test
    fun comesBackAfterARestart() {
        themeChoice().choose(AppTheme.Plum)
        assertEquals(AppTheme.Plum, themeChoice().loaded())
    }

    @Test
    fun isSavedByItsId() {
        themeChoice().choose(AppTheme.Adriatic)
        assertEquals("adriatic", file.values["theme"])
    }

    @Test
    fun aChoiceIsCurrentAtOnce() {
        val themes = themeChoice()
        themes.loaded()
        themes.choose(AppTheme.Basil)
        assertEquals(AppTheme.Basil, themes.current)
    }

    @Test
    fun choosingTheCurrentOneWritesNothing() {
        val themes = themeChoice()
        themes.choose(AppTheme.Peony)
        themes.choose(AppTheme.Peony)
        assertEquals(1, file.writes)
    }

    @Test
    fun aChoiceBeforeTheReadIsNewer() {
        themeChoice().choose(AppTheme.Olive)
        val themes = themeChoice()
        themes.choose(AppTheme.Plum)
        assertEquals(AppTheme.Plum, themes.loaded())
    }

    @Test
    fun anUnknownIdIsTheDefault() {
        // E.g. a theme a later build no longer has.
        file.values["theme"] = "sand"
        assertEquals(AppTheme.Default, themeChoice().loaded())
    }

    @Test
    fun aValueOfAnotherTypeIsTheDefault() {
        file.values["theme"] = 3
        assertEquals(AppTheme.Default, themeChoice().loaded())
    }
}
