package com.sharkula.gusle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** What a choice is saved as: changing one would lose it for whoever chose that theme. */
    @Test
    fun ids() {
        assertEquals(
            listOf("walnut", "olive", "basil", "adriatic", "plum", "peony"),
            AppTheme.entries.map { it.id },
        )
        assertEquals(AppTheme.Plum, AppTheme.fromId("plum"))
        assertEquals(AppTheme.Default, AppTheme.fromId(null))
        assertEquals(AppTheme.Default, AppTheme.fromId("sand"))
    }

    /** Each theme's main color, as in designs/v1/ICON-AND-COLORS.md. */
    @Test
    fun primaries() {
        assertEquals(
            listOf("#FFB77D", "#D1C973", "#9AD4A1", "#8AD0EE", "#DCB9F8", "#FFB2B7"),
            AppTheme.entries.map { it.colors.primary.hex() },
        )
    }

    /** Every theme on pure black, its text at least WCAG AA (4.5:1), its controls at least 3:1. */
    @Test
    fun everyThemeIsReadable() {
        for (theme in AppTheme.entries) {
            val c = theme.colors
            assertEquals("$theme background", "#000000", c.background.hex())
            val text = mapOf(
                "onPrimary on primary" to (c.onPrimary to c.primary),
                "onPrimaryContainer on primaryContainer" to (c.onPrimaryContainer to c.primaryContainer),
                "onSecondaryContainer on secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
                "onSurface on surfaceContainer" to (c.onSurface to c.surfaceContainer),
                "onSurfaceVariant on black" to (c.onSurfaceVariant to c.background),
            )
            for ((pair, colors) in text) {
                val ratio = contrast(colors.first, colors.second)
                assertTrue("$theme $pair is ${"%.2f".format(ratio)}:1", ratio >= 4.5)
            }
            // A radio button's ring when not selected, and its dot when selected.
            val controls = mapOf(
                "outline on surfaceContainer" to (c.outline to c.surfaceContainer),
                "primary on primaryContainer" to (c.primary to c.primaryContainer),
            )
            for ((pair, colors) in controls) {
                val ratio = contrast(colors.first, colors.second)
                assertTrue("$theme $pair is ${"%.2f".format(ratio)}:1", ratio >= 3)
            }
        }
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

    /** WCAG's contrast ratio. */
    private fun contrast(a: Color, b: Color): Float {
        val (la, lb) = a.luminance() to b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }
}
