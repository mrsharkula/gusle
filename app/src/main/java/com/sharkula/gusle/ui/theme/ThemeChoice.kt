package com.sharkula.gusle.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async

private const val ThemePrefs = "app_theme"
private const val ThemeKey = "theme"

/**
 * The [AppTheme] chosen in Settings, kept on disk. The app keeps one, made as its screen or its
 * tile first needs it (not for the playback service alone), and MainActivity provides its [current]
 * theme around every screen. Use it from the main thread: it reads the disk once, off it, and writes in the
 * background. It's backed up and moved to a new watch, unlike the queue in
 * [com.sharkula.gusle.playback.LastPlayed]: a theme means the same on any watch.
 */
class ThemeChoice internal constructor(
    private val onChoose: () -> Unit = {},
    openPrefs: () -> SharedPreferences,
) {
    /** [onChoose] runs when another theme is chosen, e.g. for the tile to follow it. */
    constructor(context: Context, onChoose: () -> Unit) : this(
        onChoose,
        { context.applicationContext.getSharedPreferences(ThemePrefs, Context.MODE_PRIVATE) },
    )

    private val prefs by lazy(openPrefs)

    // Starts right away, with the theme's colors, so both are usually ready before the first frame.
    private val reading = MainScope().async(Dispatchers.IO) { read().also { it.colors } }

    /**
     * The chosen theme, once it's been [load]ed or chosen; null until then. It's state: the screens
     * reading it follow a new choice.
     */
    var current: AppTheme? by mutableStateOf(null)
        private set

    /** The chosen theme, read from disk the first time, its colors made. */
    @MainThread
    suspend fun load(): AppTheme {
        current?.let { return it }
        val read = reading.await()
        // Unless one was chosen meanwhile: that's newer.
        return current ?: read.also { current = it }
    }

    /** Makes [theme] the current one, and keeps it. */
    @MainThread
    fun choose(theme: AppTheme) {
        if (theme == current) return
        current = theme
        // Written in the background (apply, not commit).
        prefs.edit { putString(ThemeKey, theme.id) }
        onChoose()
    }

    private fun read(): AppTheme =
        try {
            AppTheme.fromId(prefs.getString(ThemeKey, null))
        } catch (e: ClassCastException) {
            // A value of another type, e.g. from an earlier build: the default, rather than a crash
            // on every start.
            AppTheme.Default
        }
}
