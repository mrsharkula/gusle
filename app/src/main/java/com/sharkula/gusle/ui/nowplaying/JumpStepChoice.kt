package com.sharkula.gusle.ui.nowplaying

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

private const val JumpStepPrefs = "jump_step"
private const val JumpStepKey = "step"

/**
 * The [JumpStep] chosen in Settings, kept on disk ([SavedChoice]): for the seek screen's buttons
 * and the tile's.
 */
class JumpStepChoice internal constructor(
    onChoose: () -> Unit = {},
    openPrefs: () -> SharedPreferences,
) : SavedChoice<JumpStep>(JumpStepKey, { it.id }, { JumpStep.fromId(it) }, onChoose, openPrefs) {
    /** [onChoose] runs when another step is chosen, e.g. for the tile to follow it. */
    constructor(context: Context, onChoose: () -> Unit) : this(
        onChoose,
        { context.applicationContext.getSharedPreferences(JumpStepPrefs, Context.MODE_PRIVATE) },
    )
}
