package com.sharkula.gusle.ui.nowplaying

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

/**
 * The seek screen's steps' file: the jump step under [JumpStepKey], and the crown step under its
 * own key ([CrownStepChoice]), so the start reads one file for both. Named for the first of them,
 * which was saved there before there was a second.
 */
internal const val StepPrefs = "jump_step"
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
        { context.applicationContext.getSharedPreferences(StepPrefs, Context.MODE_PRIVATE) },
    )
}
