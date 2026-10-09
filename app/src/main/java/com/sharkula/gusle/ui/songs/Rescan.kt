package com.sharkula.gusle.ui.songs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.wear.compose.foundation.LocalScreenIsActive
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.ui.components.rememberMayAnimateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One turn of the refresh icon. */
private const val TurnMillis = 1100

/**
 * A rescan as a screen shows it: [busy] from the tap until the [library] has read the songs again,
 * but for at least one turn of the refresh icon, since a rescan usually takes milliseconds and
 * would show nothing. The icon turns only while it [mayAnimate], asked as each turn starts;
 * otherwise a turn's time passes with it standing still, so the screens say it in words as well.
 */
@Stable
class RescanState(
    private val library: MusicLibrary,
    private val mayAnimate: () -> Boolean,
    private val scope: CoroutineScope,
) {
    var busy by mutableStateOf(false)
        private set

    private val turn = Animatable(0f)

    /**
     * The refresh icon's angle in degrees. It changes every frame while the icon turns, so read it
     * only while drawing.
     */
    val rotation: Float get() = turn.value

    /** Reads the songs again, unless a rescan is shown already. */
    fun rescan() {
        if (busy) return
        busy = true
        // Ends once the songs are read, including the read again that a rescan during a read asks for.
        val read = library.refresh()
        scope.launch {
            do {
                if (mayAnimate()) {
                    turn.snapTo(0f)
                    turn.animateTo(360f, tween(TurnMillis, easing = LinearEasing))
                } else {
                    delay(TurnMillis.toLong())
                }
            } while (read.isActive)
            turn.snapTo(0f)
            busy = false
        }
    }
}

/**
 * A [RescanState] for this screen. Leaving it stops the icon, not the read. Under another screen,
 * e.g. Settings under About, the icon doesn't turn either.
 */
@Composable
fun rememberRescanState(library: MusicLibrary): RescanState {
    // States read as each turn starts, not while composing, so the screen isn't composed again each
    // time the watch's screen goes on or off.
    val mayAnimate = rememberMayAnimateState()
    val onTop = rememberUpdatedState(LocalScreenIsActive.current)
    val scope = rememberCoroutineScope()
    return remember(library, scope) { RescanState(library, { mayAnimate.value && onTop.value }, scope) }
}
