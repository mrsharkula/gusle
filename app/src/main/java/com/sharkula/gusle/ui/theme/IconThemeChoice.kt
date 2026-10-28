package com.sharkula.gusle.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

private const val IconThemeKey = "icon_follows_theme"

/**
 * Whether the launcher icon follows the chosen theme (true) or stays the default's (false): the
 * switch under the themes in Settings → Theme, off until turned on. Kept in the theme's file
 * ([ThemePrefs]) and backed up with it, as "theme" or "default". [LauncherIcon] acts on it.
 */
class IconThemeChoice internal constructor(
    onChoose: () -> Unit = {},
    openPrefs: () -> SharedPreferences,
) : SavedChoice<Boolean>(IconThemeKey, { if (it) "theme" else "default" }, { it == "theme" }, onChoose, openPrefs) {
    /** [onChoose] runs when the switch is turned, for the icon to follow. */
    constructor(context: Context, onChoose: () -> Unit) : this(
        onChoose,
        { context.applicationContext.getSharedPreferences(ThemePrefs, Context.MODE_PRIVATE) },
    )
}
