package com.sharkula.gusle.ui.nowplaying

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

private const val LengthShownKey = "length"

/**
 * What the seek screen's small time shows ([LengthShown]), kept on disk ([SavedChoice]): in the
 * steps' file ([StepPrefs]) under its own key, so the start reads one file for all three. Chosen
 * by a tap on the time itself, not in Settings.
 */
class LengthShownChoice internal constructor(
    openPrefs: () -> SharedPreferences,
) : SavedChoice<LengthShown>(LengthShownKey, { it.id }, { LengthShown.fromId(it) }, onChoose = {}, openPrefs) {
    constructor(context: Context) : this({ context.stepPrefs() })
}
