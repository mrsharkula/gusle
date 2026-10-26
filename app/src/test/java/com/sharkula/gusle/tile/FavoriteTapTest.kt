package com.sharkula.gusle.tile

import com.sharkula.gusle.ui.nowplaying.JumpStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteTapTest {
    @Test
    fun comesBackAsItWasMade() {
        for (tap in listOf(FavoriteTap(61, favorite = true), FavoriteTap(61, favorite = false), FavoriteTap(0, true))) {
            assertEquals(tap, FavoriteTap.of(tap.id))
        }
    }

    /** The other buttons, and anything else. */
    @Test
    fun anythingElseIsntOne() {
        for (id in TileButton.entries.map { it.id } + listOf(null, "", "favorite", "favorite:", "favorite:61", "favorite:x:1", "favorite:61:2")) {
            assertNull(id, FavoriteTap.of(id))
        }
    }

    @Test
    fun theOtherButtonsArentOneEither() {
        for (button in TileButton.entries) assertNull(FavoriteTap.of(button.id))
        assertNull(FavoriteTap.of(JumpTap(forward = true, JumpStep.Ten).id))
        assertNull(TileButton.of(FavoriteTap(61, true).id))
    }
}
