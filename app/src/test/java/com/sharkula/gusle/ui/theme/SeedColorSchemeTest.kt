package com.sharkula.gusle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class SeedColorSchemeTest {
    /** The Yamato cover's colors, as in DESIGN.md. */
    @Test
    fun yamato() {
        val colors = seedColorScheme(0xFF93291C.toInt())
        assertEquals("#FFB4A8", colors.primary.hex())
        assertEquals("#561E16", colors.onPrimary.hex())
        assertEquals("#73342A", colors.primaryContainer.hex())
        assertEquals("#FFDAD4", colors.onPrimaryContainer.hex())
        assertEquals("#5D3F3A", colors.secondaryContainer.hex())
        assertEquals("#FFDAD4", colors.onSecondaryContainer.hex())
        assertEquals("#271D1C", colors.surfaceContainer.hex())
        assertEquals("#F1DFDB", colors.onSurface.hex())
        assertEquals("#D8C2BE", colors.onSurfaceVariant.hex())
    }

    @Test
    fun dimRolesAreTheirRoleDarker() {
        val colors = seedColorScheme(0xFF93291C.toInt())
        assertEquals("#F19A8C", colors.primaryDim.hex())
        assertEquals("#D0A7A1", colors.secondaryDim.hex())
        assertEquals("#C7AE78", colors.tertiaryDim.hex())
        assertEquals("#FF9286", colors.errorDim.hex())
    }

    @Test
    fun backgroundStaysBlack() {
        assertEquals(Color.Black, seedColorScheme(0xFF93291C.toInt()).background)
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
}
