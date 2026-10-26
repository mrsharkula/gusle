package com.sharkula.gusle.tile

import com.sharkula.gusle.ui.nowplaying.JumpStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpTapTest {
    @Test
    fun comesBackAsItWasMade() {
        for (tap in listOf(JumpTap(forward = true, JumpStep.Five), JumpTap(forward = false, JumpStep.Fifteen), JumpTap(true, JumpStep.Ten))) {
            assertEquals(tap, JumpTap.of(tap.id))
        }
    }

    /** By the step the tile showed, back or on. */
    @Test
    fun jumpsByItsStep() {
        assertEquals(15_000L, JumpTap(forward = true, JumpStep.Fifteen).byMs)
        assertEquals(-5_000L, JumpTap(forward = false, JumpStep.Five).byMs)
    }

    /** The other buttons, a step a later build no longer has, and anything else. */
    @Test
    fun anythingElseIsntOne() {
        val others = TileButton.entries.map { it.id } + FavoriteTap(61, favorite = true).id +
            listOf(null, "", "jump", "jump:", "jump:back", "jump:back:30", "jump:up:10", "jump:forward:10:x")
        for (id in others) assertNull(id, JumpTap.of(id))
    }

    @Test
    fun theOtherButtonsArentOneEither() {
        assertNull(TileButton.of(JumpTap(forward = true, JumpStep.Ten).id))
        assertNull(FavoriteTap.of(JumpTap(forward = false, JumpStep.Ten).id))
    }
}
