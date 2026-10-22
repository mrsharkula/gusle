package com.sharkula.gusle.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Calls [onLongPress] when a finger holds still here for the watch's touch-and-hold delay, with the
 * long-press haptic ([haptics]), as holding a list row does; screen readers get it as the long-press
 * action named [label]. For what isn't a button, e.g. a title. Nothing else is taken: taps, swipes
 * and the watch's back swipe go on as before, and a finger that moves, or a touch something else
 * takes, isn't held. Once it's held, the rest of the touch is taken, so it doesn't turn into a swipe.
 */
fun Modifier.onLongPress(label: String, haptics: HapticFeedback, onLongPress: () -> Unit): Modifier =
    pointerInput(haptics, onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val letGo = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    // The final pass, after anything around it that takes the touch, e.g. a swipe.
                    val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || change.isConsumed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
            }
            // Null when the time ran out with the finger still down and still.
            if (letGo != null) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongPress()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }.semantics {
        onLongClick(label) {
            onLongPress()
            true
        }
    }
