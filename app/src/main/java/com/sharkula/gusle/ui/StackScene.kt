package com.sharkula.gusle.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.LocalSwipeToDismissBackgroundScrimColor
import androidx.wear.compose.foundation.hierarchicalFocusGroup
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import kotlinx.coroutines.delay

private const val SlideInKey = "com.sharkula.gusle.slideIn"

/**
 * How far below the top screen this one is kept, out of sight: 0 for a screen that can be seen,
 * i.e. the top one, or the one right below it while the top one slides in; 1 for the one right
 * below the top one, and so on. See [heldWhileHidden].
 */
val LocalHiddenDepth = compositionLocalOf { 0 }

/**
 * A hidden screen follows a change this long after it, once the screen on top has shown it: on the
 * watch, a song change on Now playing takes up to ~1.3 s until the new cover and its colors are up,
 * and the player is busy filling its buffer until ~2 s, which slows the frames down. The screens
 * further down follow one by one, [CatchUpStaggerMs] apart, so each is composed in a frame of its
 * own. The playback service updates its notification after them (its NotificationUpdateDelayMs).
 */
private const val CatchUpDelayMs = 2_400L
private const val CatchUpStaggerMs = 300L

/**
 * [value] as a screen shows it: as it is while the screen can be seen, but while it's hidden below
 * the top one ([LocalHiddenDepth]), only a moment after it last changed. Then a change on the top
 * screen, e.g. a song change on Now playing, doesn't also compose the screens below in the same
 * frames (in a trace with the composables named, they were about two thirds of that work). Coming
 * into view, e.g. as a swipe back starts, a screen catches up in that same frame, so it never shows
 * what it held.
 */
@Composable
fun <T> heldWhileHidden(value: T): T {
    val depth = LocalHiddenDepth.current
    val held = remember { mutableStateOf(value) }
    if (depth == 0) {
        // Read only while hidden, so this composes nothing again.
        SideEffect { held.value = value }
        return value
    }
    LaunchedEffect(value, depth) {
        if (held.value != value) {
            delay(CatchUpDelayMs + (depth - 1) * CatchUpStaggerMs)
            held.value = value
        }
    }
    return held.value
}

/** A screen's metadata when it was just opened by a tap: it slides in over the one below. */
val SlideIn: Map<String, Any> = mapOf(SlideInKey to true)

/**
 * The scenes Gusle's screens are shown in: the screen on top, with the ones below it kept composed
 * but not laid out or drawn (see [StackScene]). Below API 36, which has no predictive back, Wear's
 * own strategy handles the swipe instead.
 */
@Composable
fun <T : Any> rememberStackSceneStrategy(): SceneStrategy<T> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val reduceMotion = LocalReduceMotion.current
        remember(reduceMotion) { StackSceneStrategy(reduceMotion) }
    } else {
        rememberSwipeDismissableSceneStrategy()
    }

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
private class StackSceneStrategy<T : Any>(private val reduceMotion: Boolean) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? =
        if (entries.isEmpty()) null else StackScene(entries, reduceMotion)
}

// As long as Wear's own transitions.
private const val SlideMillis = 300

/** Going back, the screen slides off to the right, following the finger, over the one below. */
private val SlideBack = ContentTransform(
    targetContentEnter = slideInHorizontally(tween(SlideMillis, easing = LinearEasing)) { -it / 4 },
    initialContentExit = slideOutHorizontally(tween(SlideMillis, easing = LinearEasing)) { it },
)

private val NoTransition = EnterTransition.None togetherWith ExitTransition.None

/** A scene's transitions. Opening a screen swaps the scenes at once; the new one slides itself in. */
private val StackTransitions =
    NavDisplay.transitionSpec { NoTransition } +
        NavDisplay.popTransitionSpec { SlideBack } +
        NavDisplay.predictivePopTransitionSpec { SlideBack }

/**
 * The same with Reduce motion on: nothing moves by itself, but swiping back still drags the screen
 * along with the finger.
 */
private val StackTransitionsReducedMotion =
    NavDisplay.transitionSpec { NoTransition } +
        NavDisplay.popTransitionSpec { NoTransition } +
        NavDisplay.predictivePopTransitionSpec { SlideBack }

private val SlideInSpec = tween<Float>(SlideMillis, easing = FastOutSlowInEasing)

/**
 * The top entry of the back stack, and every entry below it kept composed but neither laid out nor
 * drawn. Swiping back, NavDisplay moves the one below into the next scene as it is, rather than
 * building it again: on the watch, building Home again took most of 100 ms, which froze the start
 * of the swipe. Nothing fades or shrinks either: a fading screen is drawn twice, once off-screen.
 *
 * Like Wear's own scene, it clips the top screen to the round display on a dark scrim, and only a
 * settled top screen takes focus (and with it the crown).
 */
private class StackScene<T : Any>(
    override val entries: List<NavEntry<T>>,
    private val reduceMotion: Boolean,
) : Scene<T> {
    private val top = entries.last()
    override val key: Any = top.contentKey
    override val previousEntries: List<NavEntry<T>> = entries.dropLast(1)

    // The top entry's own, if any, override the scene's, as in Wear's scene.
    override val metadata: Map<String, Any> =
        (if (reduceMotion) StackTransitionsReducedMotion else StackTransitions).let { transitions ->
            if (top.metadata.isEmpty()) transitions else transitions + top.metadata
        }

    override val content: @Composable () -> Unit = {
        val slidesIn = top.metadata[SlideInKey] == true && previousEntries.isNotEmpty() && !reduceMotion
        // Its share of the width still to go; 0 once it's in. A screen opened again while it's still
        // sliding out comes back as it is, at once: NavDisplay brings back the same one.
        val offset = remember { Animatable(if (slidesIn) 1f else 0f) }
        LaunchedEffect(offset) { if (offset.value > 0f) offset.animateTo(0f, SlideInSpec) }
        val sliding by remember { derivedStateOf { offset.value > 0f } }
        val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
        val active = lifecycle == Lifecycle.State.RESUMED && LocalScreenIsActive.current

        Box(Modifier.fillMaxSize()) {
            previousEntries.forEachIndexed { i, entry ->
                // The one right below shows while the top one slides in over it.
                Below(shown = sliding && i == previousEntries.lastIndex, depth = previousEntries.size - i) {
                    entry.Content()
                }
            }
            CompositionLocalProvider(LocalScreenIsActive provides active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = offset.value * size.width }
                        .clip(if (LocalConfiguration.current.isScreenRound) CircleShape else RectangleShape)
                        .background(LocalSwipeToDismissBackgroundScrimColor.current)
                        .hierarchicalFocusGroup(active),
                ) {
                    top.Content()
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is StackScene<*> && key == other.key && entries == other.entries

    override fun hashCode(): Int = key.hashCode() * 31 + entries.hashCode()
}

/**
 * Keeps [content] composed, but lays it out and draws it only while [shown]. Laid out nowhere, it
 * can't be touched or read out, and doesn't animate (the equalizer stops). It never takes focus,
 * and while it's shown under a screen sliding in, it takes no touches and isn't read out either.
 * It's [depth] screens below the top one.
 */
@Composable
private fun Below(shown: Boolean, depth: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalScreenIsActive provides false,
        LocalHiddenDepth provides if (shown) 0 else depth,
    ) {
        val modifier = Modifier.fillMaxSize().hierarchicalFocusGroup(active = false).clearAndSetSemantics {}
        Layout(content, if (shown) modifier.then(IgnoreTouches) else modifier) { measurables, constraints ->
            val placeables = if (shown) measurables.map { it.measure(constraints) } else emptyList()
            layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
        }
    }
}

/**
 * Takes every touch before the content does: a tap meant for the screen sliding in, or one while
 * the always-on display is over the screens, can't land here.
 */
internal val IgnoreTouches = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
