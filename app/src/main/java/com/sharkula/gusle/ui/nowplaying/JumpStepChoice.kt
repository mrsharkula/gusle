package com.sharkula.gusle.ui.nowplaying

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

/**
 * The seek screen's file: the jump step under [JumpStepKey], and under keys of their own the crown
 * step ([CrownStepChoice]) and what the small time shows ([LengthShownChoice]), so the start reads
 * one file for all three. Named for the first of them, which was saved there before the others.
 */
internal const val StepPrefs = "jump_step"
private const val JumpStepKey = "step"

/** That file, open: what every choice in it reads and writes. */
internal fun Context.stepPrefs(): SharedPreferences = applicationContext.getSharedPreferences(StepPrefs, Context.MODE_PRIVATE)

/**
 * The [JumpStep] chosen in Settings, kept on disk ([SavedChoice]): for the seek screen's buttons
 * and the tile's.
 */
class JumpStepChoice internal constructor(
    onChoose: () -> Unit = {},
    openPrefs: () -> SharedPreferences,
) : SavedChoice<JumpStep>(JumpStepKey, { it.id }, { JumpStep.fromId(it) }, onChoose, openPrefs) {
    /** [onChoose] runs when another step is chosen, e.g. for the tile to follow it. */
    constructor(context: Context, onChoose: () -> Unit) : this(onChoose, { context.stepPrefs() })
}
