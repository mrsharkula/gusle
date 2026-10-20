package com.sharkula.gusle.ui.favorites

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.sharkula.gusle.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Wear's own SwipeToReveal (Material 3) was tried first, and dropped: it tells the watch its rows
// scroll sideways, and takes swipes to the right too, so on the emulator a back swipe that started
// on a song's row went back only from its left third (designs/v2/FAVORITES.md). This one takes only
// what's its own, and looks and works the same: the button as a circle behind the row's end, a long
// swipe doing it at once, with a tick, and one row open at a time, shut as the list scrolls.

/** Between the open row and its button. */
private val ActionGap = 4.dp

/** The button's icon, as in Wear's own. */
private val ActionIconSize = 24.dp

/** A swipe past this share of the row's width does it at once, on letting go. */
private const val FullSwipeFraction = 0.5f

/** A quicker swipe than this, per second, opens or shuts the row however far it went. */
private val FlingSpeed = 400.dp

private val SettleSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)

/**
 * How far a row is swiped open: [offset] px to the left (0 or less), and whether it's moved at all
 * ([isMoved]), so its button and the cover that shuts it on a tap are composed only then. The offset
 * is read only while laying out and drawing.
 */
@Stable
class RowSwipe internal constructor() {
    var offset by mutableFloatStateOf(0f)
        private set
    var isMoved by mutableStateOf(false)
        private set

    // As the row was last composed, for a swipe or a tap that acts later: plain fields, so a row
    // costs no more state than it must.
    internal var isFavorite = false
    internal var onToggle: (favorite: Boolean) -> Unit = {}

    private var settling: Job? = null

    /** Follows a finger, stopping a row on its way somewhere. */
    internal fun dragTo(px: Float) {
        settling?.cancel()
        settling = null
        moveTo(px)
    }

    /** Goes to [px] in [scope], at once unless it may [animate]; then runs [then], also if stopped on the way. */
    internal fun settleTo(px: Float, scope: CoroutineScope, animate: Boolean, then: (() -> Unit)?) {
        settling?.cancel()
        settling = scope.launch {
            try {
                if (animate && px != offset) {
                    animate(offset, px, animationSpec = SettleSpec) { value, _ -> moveTo(value) }
                } else {
                    moveTo(px)
                }
            } finally {
                then?.invoke()
            }
        }
    }

    private fun moveTo(px: Float) {
        offset = px
        isMoved = px != 0f
    }
}

/**
 * The rows of a list that a swipe left opens ([FavoriteSwipe]): one at a time, shut as soon as the
 * list scrolls ([rememberSwipeRows]). Their animations run in the list's [scope], so what a row
 * started finishes when the row scrolls away. What every row needs is made here once: the [labels]
 * of its button and [haptics].
 */
@Stable
class SwipeRows internal constructor(
    private val scope: CoroutineScope,
    /** Wear's Reduce motion: rows go where they go at once. Remove animations takes care of itself. */
    private val reduceMotion: State<Boolean>,
    private val haptics: HapticFeedback,
    internal val labels: FavoriteLabels,
) {
    private var open: RowSwipe? = null

    /**
     * Shuts [row], with a tick, and then adds its song to the favorites, or removes it, so its button
     * says what it does until it's gone.
     */
    internal fun act(row: RowSwipe) {
        val adding = !row.isFavorite
        val toggle = row.onToggle
        haptics.performHapticFeedback(if (adding) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        settle(row, 0f) { toggle(adding) }
    }

    /** As a swipe gets far enough to do it at once. */
    internal fun fullSwipeReached() {
        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    /** [row] starts to move: another one open shuts. */
    internal fun moving(row: RowSwipe) {
        open?.takeIf { it !== row }?.let { settle(it, 0f) }
        open = row
    }

    /** Takes [row] to [px], open at its button or shut (0), and then runs [then]. */
    internal fun settle(row: RowSwipe, px: Float, then: (() -> Unit)? = null) {
        if (px == 0f && open === row) open = null
        row.settleTo(px, scope, animate = !reduceMotion.value, then)
    }

    /** Shuts the row that's open, if one is. */
    internal fun shutOpen() {
        open?.let { settle(it, 0f) }
    }
}

/** What a row's button says, to add its song or to remove it; also the row's action for screen readers. */
@Immutable
class FavoriteLabels(val add: String, val remove: String)

/** The rows of the list with [listState] that a swipe opens; the one open shuts as soon as it scrolls. */
@Composable
fun rememberSwipeRows(listState: TransformingLazyColumnState): SwipeRows {
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberUpdatedState(LocalReduceMotion.current)
    val haptics = LocalHapticFeedback.current
    val add = stringResource(R.string.add_to_favorites)
    val remove = stringResource(R.string.remove_from_favorites)
    val rows = remember(scope, reduceMotion, haptics, add, remove) {
        SwipeRows(scope, reduceMotion, haptics, FavoriteLabels(add, remove))
    }
    // One watch for the whole list, rather than each row reading whether it scrolls.
    LaunchedEffect(listState, rows) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (scrolling) rows.shutOpen() }
    }
    return rows
}

/**
 * A song's row ([content]) that a swipe left opens on a round button behind its end, which adds the
 * song to the favorites, or removes it if [isFavorite]; a swipe past half the row does it at once.
 * The row shuts, and then [onToggle] gets what it's to be, so the button says what it does until
 * it's gone. A tap on an open row shuts it, rather than playing the song. Only swipes to the left are
 * the row's (and back to the right on an open one): up and down are the list's, and a swipe to the
 * right is back, as everywhere. Screen readers get the same as an action on the row: [content] gets
 * the modifier that offers it.
 *
 * It's the list item itself, so it takes the item's height as it shrinks toward the round edges
 * ([transformationSpec]): the list reads that only from an item's own layout. Its swipe goes with the
 * [song] (e.g. a song's ID), so a row that comes to show another song, as in the Queue, starts shut.
 */
@Composable
fun TransformingLazyColumnItemScope.FavoriteSwipe(
    song: Any,
    isFavorite: Boolean,
    onToggle: (favorite: Boolean) -> Unit,
    rows: SwipeRows,
    transformationSpec: TransformationSpec,
    content: @Composable (actions: Modifier) -> Unit,
) {
    val swipe = remember(song) { RowSwipe() }
    swipe.isFavorite = isFavorite
    swipe.onToggle = onToggle
    val label = if (isFavorite) rows.labels.remove else rows.labels.add
    Box(
        Modifier
            .transformedHeight(this, transformationSpec)
            .pointerInput(swipe, rows) { detectRowSwipe(swipe, rows) },
    ) {
        if (swipe.isMoved) SwipeAction(swipe, isFavorite, label, onClick = { rows.act(swipe) })
        // Moved as it's placed, without a layer of its own for every row; with it, where it takes taps.
        Box(Modifier.offset { IntOffset(swipe.offset.roundToInt(), 0) }) {
            content(
                Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(label) {
                            swipe.onToggle(!swipe.isFavorite)
                            true
                        },
                    )
                },
            )
            if (swipe.isMoved) {
                Box(
                    Modifier.matchParentSize().pointerInput(swipe, rows) { detectTapGestures { rows.settle(swipe, 0f) } },
                )
            }
        }
    }
}

/**
 * The button behind a row as it opens: as wide as the row has moved (less a gap), a circle once
 * it's open and wider as it's swiped further, its icon fading in. It adds the song, or removes it
 * when it's a favorite ([isFavorite]), with [onClick].
 */
@Composable
private fun BoxScope.SwipeAction(swipe: RowSwipe, isFavorite: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val width = (-swipe.offset - ActionGap.toPx()).roundToInt().coerceIn(0, constraints.maxWidth)
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(width, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .clip(CircleShape)
                .background(if (isFavorite) colors.secondaryContainer else colors.primary)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isFavorite) R.drawable.ic_heart_minus else R.drawable.ic_heart_plus),
                contentDescription = null,
                modifier = Modifier
                    .size(ActionIconSize)
                    .graphicsLayer {
                        // In from a third of the circle's width to all of it.
                        val circle = size.height.coerceAtLeast(1f)
                        alpha = ((-swipe.offset - circle / 3) / (circle * 2 / 3)).coerceIn(0f, 1f)
                    },
                tint = if (isFavorite) colors.onSecondaryContainer else colors.onPrimary,
            )
        }
    }
}

/**
 * Follows a swipe on a row: to the left, or back to the right on a row that's open. Anything else,
 * up, down or to the right on a shut row, is left alone, untouched, for the list and the watch's
 * back swipe. On letting go, the row opens at its button, shuts, or past [FullSwipeFraction] of its
 * width, does it at once, with a tick as it gets that far.
 */
private suspend fun PointerInputScope.detectRowSwipe(swipe: RowSwipe, rows: SwipeRows) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val velocity = VelocityTracker().apply { addPointerInputChange(down) }
    val start = swipe.offset
    var from = Float.NaN
    var pastFull = false
    while (true) {
        // The main pass, after the row: what's taken here, it sees taken and doesn't take as a tap.
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
        velocity.addPointerInputChange(change)
        if (from.isNaN()) {
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            val moved = change.position - down.position
            if (moved.getDistance() < viewConfiguration.touchSlop) continue
            if (abs(moved.y) > abs(moved.x) || (moved.x > 0 && start == 0f)) return@awaitEachGesture
            from = change.position.x
            rows.moving(swipe)
        }
        val openPx = -(size.height + ActionGap.toPx())
        val fullPx = -size.width * FullSwipeFraction
        if (!change.pressed) {
            // Taken already: not let go, but cancelled, e.g. by the watch's back swipe or the screen
            // going off. The row goes back to where it was, and nothing's added or removed.
            if (change.isConsumed) {
                rows.settle(swipe, start)
                return@awaitEachGesture
            }
            change.consume()
            val speed = velocity.calculateVelocity().x
            val fling = FlingSpeed.toPx()
            when {
                swipe.offset <= fullPx -> rows.act(swipe)
                speed <= -fling -> rows.settle(swipe, openPx)
                speed >= fling -> rows.settle(swipe, 0f)
                else -> rows.settle(swipe, if (swipe.offset < openPx / 2) openPx else 0f)
            }
            return@awaitEachGesture
        }
        val offset = (start + change.position.x - from).coerceIn(-size.width.toFloat(), 0f)
        if ((offset <= fullPx) != pastFull) {
            pastFull = offset <= fullPx
            if (pastFull) rows.fullSwipeReached()
        }
        swipe.dragTo(offset)
        change.consume()
    }
}
