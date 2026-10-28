package com.sharkula.gusle.ui.theme

import com.sharkula.gusle.FakePrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What's this choice's own; the rest is SavedChoiceTest's. */
class IconThemeChoiceTest {
    /** The file on "disk"; a new [IconThemeChoice] on it is Gusle started again. */
    private val file = FakePrefs()

    private fun iconThemeChoice() = IconThemeChoice { file }

    private fun IconThemeChoice.loaded() = runBlocking { load() }

    @Test
    fun isOffBeforeItsChosen() {
        assertEquals(false, iconThemeChoice().loaded())
    }

    @Test
    fun isSavedAsWords() {
        iconThemeChoice().choose(true)
        // In the theme's file, under its own key, next to the theme's ("theme").
        assertEquals("theme", file.values["icon_follows_theme"])
        assertEquals(true, iconThemeChoice().loaded())
        iconThemeChoice().choose(false)
        assertEquals("default", file.values["icon_follows_theme"])
        assertEquals(false, iconThemeChoice().loaded())
    }

    @Test
    fun anUnknownValueIsOff() {
        file.values["icon_follows_theme"] = "yes"
        assertEquals(false, iconThemeChoice().loaded())
    }
}
