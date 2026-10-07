package com.sharkula.gusle.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlinx.coroutines.delay

/** Three bars, 3 dp wide with round ends and 2 dp apart, at most 14 dp tall. */
private const val Bars = 3
private val BarWidth = 3.dp
private val BarGap = 2.dp
private val EqualizerWidth = BarWidth * Bars + BarGap * (Bars - 1)
private val EqualizerHeight = 14.dp

/** The bars move between these parts of the full height. */
private const val LowHeight = 0.25f
private const val HighHeight = 1f

/** How long each bar takes to go up, and again to come down. */
private val BarWayNanos = longArrayOf(900_000_000, 700_000_000, 1_100_000_000)

/** The bars' heights while a song plays but nothing may move. */
private val StillHeights = floatArrayOf(0.5f, 1f, 0.7f)

/** A pause takes the bars down low over this long, and playing again sets them off over this long. */
private const val SwingChangeNanos = 300_000_000L

/**
 * The bars are drawn anew 30 times a second, every other frame of the watch's screen: at their
 * size and speed the steps don't show, and each frame draws the whole screen again.
 */
private const val FrameMs = 1000L / 30

/**
 * The playing song's equalizer: three bars that move up and down while it plays, and sit low and
 * even while it's paused. While they may not move (see [rememberMayAnimate]), or aren't on the
 * screen, a playing song's bars stand still at different heights. It's only a picture, which
 * accessibility services skip; the row says whether it plays.
 */
@Composable
fun Equalizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    // Off the screen, e.g. laid out just past the end of a list, they'd move unseen.
    var visible by remember { mutableStateOf(false) }
    val animate = rememberMayAnimate() && visible
    val color = MaterialTheme.colorScheme.primary
    // How far the bars swing, from low and still (0) to all the way (1), and how long they've been
    // moving. Both are read only while drawing, so a frame draws just the bars again.
    val swing = remember { mutableFloatStateOf(if (isPlaying) 1f else 0f) }
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, animate) {
        val target = if (isPlaying) 1f else 0f
        if (!animate) {
            swing.floatValue = target
            // When they may move again, they set off from where they stand.
            clock.longValue = 0L
            return@LaunchedEffect
        }
        // From low and still they set off afresh; otherwise they go on from where they are.
        if (swing.floatValue == 0f) clock.longValue = 0L
        val startSwing = swing.floatValue
        val startClock = clock.longValue
        var startNanos = -1L
        // The bars move while playing, and while going down after a pause.
        while (isPlaying || swing.floatValue != target) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val elapsed = now - startNanos
                clock.longValue = startClock + elapsed
                swing.floatValue = if (elapsed >= SwingChangeNanos) {
                    target
                } else {
                    lerp(startSwing, target, FastOutSlowInEasing.transform(elapsed.toFloat() / SwingChangeNanos))
                }
            }
            // Sleeps through the frame in between rather than waking for it: the frame after this
            // wait is the one due a [FrameMs] after the last.
            delay(FrameMs * 3 / 4)
        }
    }
    Spacer(
        modifier
            .onVisibilityChanged(minFractionVisible = 0f) { visible = it }
            .size(EqualizerWidth, EqualizerHeight)
            // A layer of its own, so a frame draws just the bars again.
            .graphicsLayer()
            .drawWithCache {
                val barWidth = BarWidth.toPx()
                val step = barWidth + BarGap.toPx()
                val ends = CornerRadius(barWidth / 2)
                onDrawBehind {
                    for (bar in 0 until Bars) {
                        val height = size.height * when {
                            animate -> lerp(LowHeight, barHeight(bar, clock.longValue), swing.floatValue)
                            isPlaying -> StillHeights[bar]
                            else -> LowHeight
                        }
                        drawRoundRect(color, Offset(bar * step, size.height - height), Size(barWidth, height), ends)
                    }
                }
            },
    )
}

/**
 * The height of [bar] [nanos] after the bars set off, as a part of the full height: it goes up and
 * down between [LowHeight] and [HighHeight], easing in and out, [BarWayNanos] each way. It sets off
 * from its [StillHeights] on its way up (the tallest on its way down), so bars standing still
 * start moving without a jump.
 */
internal fun barHeight(bar: Int, nanos: Long): Float {
    val way = BarWayNanos[bar]
    val time = nanos + BarStartNanos[bar]
    val along = (time % way).toFloat() / way
    val up = if (time / way % 2 == 0L) along else 1f - along
    return LowHeight + (HighHeight - LowHeight) * EaseInOut.transform(up)
}

/** How far into its movement each bar sets off: where it's at its still height on its way up. */
private val BarStartNanos = LongArray(Bars) { bar -> (wayUpTo(StillHeights[bar]) * BarWayNanos[bar]).toLong() }

/** How far along its way up (0 to 1) a bar is at [height]: [barHeight]'s easing, undone. */
private fun wayUpTo(height: Float): Float {
    val eased = (height - LowHeight) / (HighHeight - LowHeight)
    var low = 0f
    var high = 1f
    repeat(24) {
        val middle = (low + high) / 2
        if (EaseInOut.transform(middle) < eased) low = middle else high = middle
    }
    return (low + high) / 2
}

/** Playing (standing still, as in a preview nothing moves), then paused. */
@Preview
@Composable
private fun EqualizerPreview() {
    GusleTheme {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Equalizer(isPlaying = true)
            Equalizer(isPlaying = false)
        }
    }
}
