package com.sharkula.gusle.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gusle's own colors, for the screens while nothing is playing, or the song has no colors of its
 * own (no cover, or one with no real color in it). Each is a seed made into a scheme by the same
 * rules as a cover's colors ([seedColorScheme]), so it sits with them. Those rules keep only a
 * seed's hue, so the seeds' hues are far apart. One is chosen in Settings → Theme and kept by
 * [ThemeChoice]; the seeds, and the colors considered, are in designs/v1/ICON-AND-COLORS.md. The
 * launcher icon keeps walnut's colors whatever the theme (res/values/colors.xml).
 */
enum class AppTheme(
    /** What the choice is saved as, so renaming or moving an entry doesn't change what was chosen. */
    val id: String,
    @StringRes val label: Int,
    val seed: Int,
) {
    /** The walnut a gusle's body is carved from. */
    Walnut("walnut", R.string.theme_walnut, 0xFFC49A7A.toInt()),

    /** A muted olive, the user's idea; made into a soft yellow-olive. */
    Olive("olive", R.string.theme_olive, 0xFFB7B176.toInt()),

    /** Bosiljak, the basil of Balkan folk songs and customs. */
    Basil("basil", R.string.theme_basil, 0xFF4E8A5A.toInt()),

    /** The sea off Montenegro and Dalmatia. */
    Adriatic("adriatic", R.string.theme_adriatic, 0xFF2386A6.toInt()),

    /** Šljiva, the Balkan plum. */
    Plum("plum", R.string.theme_plum, 0xFF5B3F73.toInt()),

    /** Božur, the red peony of the epic songs sung to the gusle. */
    Peony("peony", R.string.theme_peony, 0xFFA4243B.toInt());

    private val scheme = lazy { seedColorScheme(seed) }

    /**
     * The scheme, made the first time it's needed. That takes a while, the first one most, so Gusle
     * makes them in the background before they're needed: the chosen one as its screen starts
     * ([ThemeChoice]), the others as Settings opens ([makeAllColors]).
     */
    val colors: ColorScheme by scheme

    /** Whether [colors] is made already. */
    val hasColors: Boolean get() = scheme.isInitialized()

    companion object {
        /** The theme until another is chosen. */
        val Default = Walnut

        /** The theme saved as [id]; [Default] when there's none, or it's no longer one of them. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: Default

        /** Makes every theme's colors off the main thread, so showing or choosing one doesn't wait. */
        suspend fun makeAllColors() = withContext(Dispatchers.Default) { entries.forEach { it.colors } }
    }
}

/**
 * The app theme every screen shows unless a song's cover gives it other colors. MainActivity
 * provides the chosen one around the whole app. It's static: a new choice composes every screen
 * again at once, the ones below the top one too.
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
