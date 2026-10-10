package com.sharkula.gusle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Gusle's own colors, for the screens while nothing is playing, or the song has no colors of its
 * own (no cover, or one with no real color in it). Each is a seed made into a scheme by the same
 * rules as a cover's colors ([seedColorScheme]), so it sits with them. A choice of themes in
 * Settings would list these and provide the chosen one as [LocalAppTheme]; the colors considered,
 * with their seeds, are in designs/v1/ICON-AND-COLORS.md. The launcher icon keeps walnut's colors
 * whatever the theme (res/values/colors.xml).
 */
enum class AppTheme(val seed: Int) {
    /** The walnut a gusle's body is carved from. */
    Walnut(0xFFC49A7A.toInt());

    /**
     * The scheme, made the first time it's needed. The first one made takes a while, so Gusle makes
     * the [Default] one in the background as its screen starts (see MainActivity).
     */
    val colors: ColorScheme by lazy { seedColorScheme(seed) }

    companion object {
        /** The theme until another is chosen. */
        val Default = Walnut
    }
}

/**
 * The app theme every screen shows unless a song's cover gives it other colors. It's
 * [AppTheme.Default] for now; a choice in Settings would provide the chosen one around the whole
 * app (MainActivity), which changes every screen at once.
 */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.Default }

/** Gusle's Material theme in [colorScheme], e.g. a song's; in the app theme's colors when it's null. */
@Composable
fun GusleTheme(
    colorScheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme ?: LocalAppTheme.current.colors, content = content)
}
