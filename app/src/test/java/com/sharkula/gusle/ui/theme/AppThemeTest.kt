package com.sharkula.gusle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    /** Walnut's colors, as in DESIGN.md. */
    @Test
    fun walnut() {
        val colors = AppTheme.Walnut.colors
        assertEquals("#FFB77D", colors.primary.hex())
        assertEquals("#4D2600", colors.onPrimary.hex())
        assertEquals("#6C3A07", colors.primaryContainer.hex())
        assertEquals("#FFDCC3", colors.onPrimaryContainer.hex())
        assertEquals("#5A422E", colors.secondaryContainer.hex())
        assertEquals("#FFDCC3", colors.onSecondaryContainer.hex())
        assertEquals("#261E18", colors.surfaceContainer.hex())
        assertEquals("#EFE0D6", colors.onSurface.hex())
        assertEquals("#D6C3B6", colors.onSurfaceVariant.hex())
    }

    /** The launcher icon is drawn in walnut's primary, onPrimary and primaryDim. */
    @Test
    fun launcherIconIsInWalnutsColors() {
        // Unit tests run in the module's folder.
        val xml = File("src/main/res/values/colors.xml").readText()
        fun color(name: String) =
            Regex("""<color name="$name">(#[0-9A-Fa-f]{6})</color>""").find(xml)?.groupValues?.get(1)?.uppercase()
        val colors = AppTheme.Walnut.colors
        assertEquals(colors.primary.hex(), color("ic_launcher_background"))
        assertEquals(colors.onPrimary.hex(), color("ic_launcher_foreground"))
        assertEquals(colors.primaryDim.hex(), color("ic_launcher_skin"))
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)
}
