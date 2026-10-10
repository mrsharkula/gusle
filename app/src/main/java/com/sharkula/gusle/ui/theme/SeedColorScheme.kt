package com.sharkula.gusle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.SchemeTonalSpot
import kotlin.math.roundToInt

/**
 * Wear's dim roles are their role's palette this many tones darker, as in Gusle's first, hand-made
 * scheme (80 → 72).
 */
private const val DimTones = 8

/**
 * Material 3's dark "tonal spot" scheme around [seed] (the 2021 rules, the library's default), in
 * Wear's roles, on pure black. Material's schemes have no dim roles; see [DimTones]. A cover's
 * colors are made this way from its main color, and each [AppTheme] from its seed.
 */
fun seedColorScheme(seed: Int): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), isDark = true, contrastLevel = 0.0)
    val roles = MaterialDynamicColors()
    fun color(role: DynamicColor) = Color(role.getArgb(scheme))
    fun dim(role: DynamicColor, palette: TonalPalette) =
        Color(palette.tone((role.getHct(scheme).tone - DimTones).roundToInt()))
    return ColorScheme(
        primary = color(roles.primary()),
        primaryDim = dim(roles.primary(), scheme.primaryPalette),
        primaryContainer = color(roles.primaryContainer()),
        onPrimary = color(roles.onPrimary()),
        onPrimaryContainer = color(roles.onPrimaryContainer()),
        secondary = color(roles.secondary()),
        secondaryDim = dim(roles.secondary(), scheme.secondaryPalette),
        secondaryContainer = color(roles.secondaryContainer()),
        onSecondary = color(roles.onSecondary()),
        onSecondaryContainer = color(roles.onSecondaryContainer()),
        tertiary = color(roles.tertiary()),
        tertiaryDim = dim(roles.tertiary(), scheme.tertiaryPalette),
        tertiaryContainer = color(roles.tertiaryContainer()),
        onTertiary = color(roles.onTertiary()),
        onTertiaryContainer = color(roles.onTertiaryContainer()),
        surfaceContainerLow = color(roles.surfaceContainerLow()),
        surfaceContainer = color(roles.surfaceContainer()),
        surfaceContainerHigh = color(roles.surfaceContainerHigh()),
        onSurface = color(roles.onSurface()),
        onSurfaceVariant = color(roles.onSurfaceVariant()),
        outline = color(roles.outline()),
        outlineVariant = color(roles.outlineVariant()),
        // Unused pixels stay off.
        background = Color.Black,
        onBackground = color(roles.onBackground()),
        error = color(roles.error()),
        errorDim = dim(roles.error(), scheme.errorPalette),
        errorContainer = color(roles.errorContainer()),
        onError = color(roles.onError()),
        onErrorContainer = color(roles.onErrorContainer()),
    )
}
