package com.sharkula.gusle.ui.nowplaying

import androidx.compose.runtime.compositionLocalOf

/**
 * How far a step of the crown moves the song on the seek screen: chosen in Settings → Crown step,
 * and kept by [CrownStepChoice]. It's a slow turn's step: steps that come quickly move two and
 * four times as far, whichever is chosen ([SeekSteps]). The − and + buttons' step is its own choice
 * ([JumpStep]).
 */
enum class CrownStep(
    /** What the choice is saved as, so renaming or moving an entry doesn't change what was chosen. */
    val id: String,
    /** How far a slow step moves. */
    val seconds: Int,
) {
    One("1", 1),
    Four("4", 4),
    Seven("7", 7);

    /** How far a slow step moves, in ms. */
    val ms: Long get() = seconds * 1_000L

    companion object {
        /** The step until another is chosen: the middle one, nearest the 5 s the crown moved before there was a choice. */
        val Default = Four

        /** The step saved as [id]; [Default] when there's none, or it's no longer one of them. */
        fun fromId(id: String?): CrownStep = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The step chosen in Settings, for the seek screen's crown and the Settings screens that show it.
 * MainActivity provides it around the whole app, as it does the jump step.
 */
val LocalCrownStep = compositionLocalOf { CrownStep.Default }
