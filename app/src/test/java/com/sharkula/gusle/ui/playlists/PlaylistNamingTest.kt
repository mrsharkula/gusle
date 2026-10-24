package com.sharkula.gusle.ui.playlists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNamingTest {
    @Test
    fun aNameIsAsTyped() {
        assertEquals("Road trip", tidyName("Road trip"))
    }

    /** Wear's keyboard can send a name of spaces only. */
    @Test
    fun spacesAroundItGo() {
        assertEquals("Road trip", tidyName("  Road trip "))
        assertNull(tidyName(" "))
        assertNull(tidyName(""))
    }

    @Test
    fun itsOnOneLine() {
        assertEquals("Road trip 2", tidyName("Road trip\n2\r\n"))
        assertNull(tidyName("\n"))
    }
}
