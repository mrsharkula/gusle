package com.sharkula.gusle.ui.nowplaying

import android.content.Context
import android.content.SharedPreferences
import com.sharkula.gusle.ui.settings.SavedChoice

private const val CrownStepKey = "crown"

/**
 * The [CrownStep] chosen in Settings, kept on disk ([SavedChoice]) for the seek screen's crown: in
 * the jump step's file ([StepPrefs]) under its own key, as what the small time shows is too
 * ([LengthShownChoice]), so the start reads one file for all three.
 */
class CrownStepChoice internal constructor(
    openPrefs: () -> SharedPreferences,
) : SavedChoice<CrownStep>(CrownStepKey, { it.id }, { CrownStep.fromId(it) }, onChoose = {}, openPrefs) {
    constructor(context: Context) : this({ context.stepPrefs() })
}
