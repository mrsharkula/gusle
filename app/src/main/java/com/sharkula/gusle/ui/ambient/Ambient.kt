package com.sharkula.gusle.ui.ambient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import com.sharkula.gusle.ui.IgnoreTouches

/**
 * Whether the watch shows Gusle's always-on display: while Gusle is open and the wrist goes down,
 * or a palm covers the watch. MainActivity's AmbientLifecycleObserver calls it. That tells the
 * watch about the activity, not a screen, and can't be taken back, so every screen gets it.
 */
@Stable
class AmbientState : AmbientLifecycleObserver.AmbientLifecycleCallback {
    /** Whether the watch is in its always-on display, showing Gusle's. */
    var isAmbient by mutableStateOf(false)
        private set

    /** Whether the screen can burn in: then what's shown moves a little at each update. */
    var burnInProtection by mutableStateOf(false)
        private set

    /** Whether the screen shows only a few colors in ambient, without antialiasing. */
    var lowBit by mutableStateOf(false)
        private set

    /**
     * Counts the watch's updates, about one a minute, and the times it went into ambient: each one
     * reads the time and the song's position anew, and moves what's shown on burn-in screens.
     */
    var updates by mutableIntStateOf(0)
        private set

    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
        burnInProtection = ambientDetails.burnInProtectionRequired
        lowBit = ambientDetails.deviceHasLowBitAmbient
        updates++
        isAmbient = true
    }

    override fun onUpdateAmbient() {
        updates++
    }

    override fun onExitAmbient() {
        isAmbient = false
    }

    /**
     * Catches up with the watch, which says [inAmbient], in case a call was missed while Gusle was
     * off the screen, e.g. under the watch face.
     */
    fun sync(inAmbient: Boolean) {
        if (inAmbient == isAmbient) return
        if (inAmbient) updates++
        isAmbient = inAmbient
    }
}

/** MainActivity's [AmbientState]; one that never goes into ambient elsewhere, e.g. in previews. */
val LocalAmbient = staticCompositionLocalOf { AmbientState() }

/**
 * [content], under Gusle's always-on display while the watch is in ambient ([state]). The screens
 * stay as they are underneath, composed and laid out, so when the wrist comes up the next frame
 * shows them as they were; meanwhile they're not drawn, focused (the crown), touched or read out.
 * Nothing on them moves either: what moves waits for rememberScreenShown, which is false in ambient.
 * Going in and out of ambient changes only these modifiers, and composes none of the screens again.
 */
@Composable
fun AmbientMode(state: AmbientState, content: @Composable () -> Unit) {
    val inAmbient = state.isAmbient
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (inAmbient) 0f else 1f }
                .hierarchicalFocusGroup(active = !inAmbient)
                .then(if (inAmbient) Modifier.clearAndSetSemantics {}.then(IgnoreTouches) else Modifier),
        ) {
            content()
        }
        if (inAmbient) AmbientScreen(state)
    }
}
