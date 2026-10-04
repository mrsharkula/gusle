package com.sharkula.gusle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// Warm sand, used when nothing is playing or the song has no cover. Songs with a cover
// get a scheme generated from it instead. Every role is set, so no component falls back
// to the library's baseline colors; tones are derived from the primary's hue.
val DefaultColorScheme = ColorScheme(
    primary = Color(0xFFF0C987),
    primaryDim = Color(0xFFD1AB68),
    primaryContainer = Color(0xFF4B3A1F),
    onPrimary = Color(0xFF3D2A00),
    onPrimaryContainer = Color(0xFFFFE8C2),
    secondary = Color(0xFFD6C4A9),
    secondaryDim = Color(0xFFC0AE94),
    secondaryContainer = Color(0xFF42362A),
    onSecondary = Color(0xFF3B2F19),
    onSecondaryContainer = Color(0xFFF5E6CC),
    tertiary = Color(0xFFACD0AA),
    tertiaryDim = Color(0xFF97BA95),
    tertiaryContainer = Color(0xFF2F4E2F),
    onTertiary = Color(0xFF18371A),
    onTertiaryContainer = Color(0xFFC8ECC6),
    surfaceContainerLow = Color(0xFF1E1B16),
    surfaceContainer = Color(0xFF262523),
    surfaceContainerHigh = Color(0xFF38342F),
    onSurface = Color(0xFFF6EFE3),
    onSurfaceVariant = Color(0xFFB9AD98),
    outline = Color(0xFF988F83),
    outlineVariant = Color(0xFF4D463A),
    background = Color.Black,
    onBackground = Color(0xFFF6EFE3),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE89A92),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun GusleTheme(
    colorScheme: ColorScheme = DefaultColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
