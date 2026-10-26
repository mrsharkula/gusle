package com.sharkula.gusle.tile

import com.sharkula.gusle.ui.nowplaying.JumpStep
import org.junit.Assert.assertEquals
import org.junit.Test

class TileIconTest {
    /** A step's icons are looked up by their drawables as the tile is made: a step without them would crash it. */
    @Test
    fun everyStepHasItsIconsOnTheTile() {
        for (step in JumpStep.entries) {
            assertEquals(step.name, step.backIcon, TileIcon.back(step).drawable)
            assertEquals(step.name, step.forwardIcon, TileIcon.forward(step).drawable)
        }
    }
}
