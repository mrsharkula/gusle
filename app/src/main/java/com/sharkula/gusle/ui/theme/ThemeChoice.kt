package com.sharkula.gusle.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

private const val ThemePrefs = "app_theme"
private const val ThemeKey = "theme"

/**
 * The [AppTheme] chosen in Settings, kept on disk ([SavedChoice]). Its colors are made as it's
 * read, so both are usually ready before the first frame. Made as a screen or the tile first needs
 * it, not for the playback service alone, which has no screen to color.
 */
class ThemeChoice internal constructor(
    onChoose: () -> Unit = {},
    openPrefs: () -> SharedPreferences,
) : SavedChoice<AppTheme>(ThemeKey, { it.id }, { AppTheme.fromId(it) }, onChoose, openPrefs, prepare = { it.colors }) {
    /** [onChoose] runs when another theme is chosen, e.g. for the tile to follow it. */
    constructor(context: Context, onChoose: () -> Unit) : this(
        onChoose,
        { context.applicationContext.getSharedPreferences(ThemePrefs, Context.MODE_PRIVATE) },
    )
}
