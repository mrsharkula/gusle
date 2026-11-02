package com.sharkula.gusle.ui.nowplaying

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.compositionLocalOf
import com.sharkula.gusle.R

/**
 * How far the − and + buttons jump, on the seek screen and on the tile: chosen in Settings → Jump
 * by, and kept by [JumpStepChoice]. Each step has its own icons, with its number in them, and the
 * buttons' labels for screen readers say it ([backLabel], [forwardLabel]). The crown's step on the
 * seek screen is its own choice ([CrownStep]), whatever is chosen here.
 */
enum class JumpStep(
    /** What the choice is saved as, so renaming or moving an entry doesn't change what was chosen. */
    val id: String,
    /** How far a jump goes. */
    val seconds: Int,
    /** The − and + buttons' icons: Material's replay and forward ones, with the number in them. */
    @DrawableRes val backIcon: Int,
    @DrawableRes val forwardIcon: Int,
) {
    Five("5", 5, R.drawable.ic_replay_5, R.drawable.ic_forward_5),
    Ten("10", 10, R.drawable.ic_replay_10, R.drawable.ic_forward_10),

    /** Material Symbols has no 15 s icons: Gusle's are drawn from its 5 and 10 ones (designs/notes/SEEK-STEP.md). */
    Fifteen("15", 15, R.drawable.ic_replay_15, R.drawable.ic_forward_15);

    /** How far a jump goes, in ms. */
    val ms: Long get() = seconds * 1_000L

    /** The − button's label for screen readers: "Back 10 seconds". */
    fun backLabel(resources: Resources): String = resources.getQuantityString(R.plurals.back_seconds, seconds, seconds)

    /** The + button's label for screen readers: "Forward 10 seconds". */
    fun forwardLabel(resources: Resources): String = resources.getQuantityString(R.plurals.forward_seconds, seconds, seconds)

    companion object {
        /** The step until another is chosen: how far the buttons always jumped. */
        val Default = Ten

        /** The step saved as [id]; [Default] when there's none, or it's no longer one of them. */
        fun fromId(id: String?): JumpStep = entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * The step chosen in Settings, for the seek screen's buttons and the Settings screens that show
 * it. MainActivity provides it around the whole app, as it does the theme, but not statically: a
 * new choice composes again only the screens that read it, not every screen, as a new theme must.
 */
val LocalJumpStep = compositionLocalOf { JumpStep.Default }
