package com.sharkula.gusle.ui.favorites

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.R
import com.sharkula.gusle.data.FavoriteSongs
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.favorites
import com.sharkula.gusle.ui.heldWhileHidden
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The favorite songs, for a screen to show: as they are while it can be seen, a moment later while
 * it's hidden below another one ([heldWhileHidden]), so a heart tapped on top doesn't compose the
 * screens below in the same frames.
 */
@Composable
fun rememberFavorites(): FavoriteSongs {
    val favorites by LocalContext.current.favorites.songs.collectAsStateWithLifecycle()
    return heldWhileHidden(favorites)
}

/** Adds a song to the favorites, or removes it if not `favorite`: what the screens' hearts and swipes call. */
@Composable
fun rememberSetFavorite(): (song: Song, favorite: Boolean) -> Unit {
    val favorites = LocalContext.current.favorites
    return remember(favorites) { favorites::set }
}

/** The heart takes taps this far around it: the smallest touch target. */
private val HeartTouchTarget = 48.dp

/** Adding grows the heart to this and back, as it fills from the middle; removing dips it to [RemoveDip]. */
private const val AddPeak = 1.28f
private const val RemoveDip = 0.84f
private const val AddMs = 260
private const val RemoveMs = 160
private const val FillMs = 200
private const val DrainMs = 120

/** Material's emphasized decelerate. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * A heart that says whether a song is a favorite ([isFavorite]): outlined in [outlineColor] when it
 * isn't, filled in the primary color when it is, [iconSize] across, with a touch target of the
 * smallest size around it. A tap calls [onToggle] with what it's to be, with a haptic tick; adding
 * fills it from the middle while it grows and shrinks back, removing dips it ([HeartTap]). With
 * Reduce motion, or when the state changes elsewhere (e.g. the next song, or the tile), it just
 * switches. Null [onToggle] disables it, e.g. with no song.
 */
@Composable
fun FavoriteHeart(
    isFavorite: Boolean,
    onToggle: ((favorite: Boolean) -> Unit)?,
    outlineColor: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val tap = rememberHeartTap()
    val label = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)
    Box(
        modifier
            .size(HeartTouchTarget)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = onToggle != null, role = Role.Button) {
                onToggle?.let { tap.tap(adding = !isFavorite, it) }
            },
        contentAlignment = Alignment.Center,
    ) {
        HeartIcon(isFavorite, tap, outlineColor, Modifier.size(iconSize))
    }
}

/**
 * What a tap on a favorite heart does, for [FavoriteHeart] and for a button that carries the heart:
 * a haptic tick, the change itself, and the heart's fill and size as they move, which [HeartIcon]
 * draws; with Wear's Reduce motion ([reduceMotion]), it just switches.
 */
@Stable
class HeartTap internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val reduceMotion: State<Boolean>,
) {
    // How filled the heart is while a tap's fill is on its way; otherwise it's as the state says.
    internal val fill = Animatable(0f)
    internal val scale = Animatable(1f)

    /**
     * Ticks, and gives [onToggle] what it's to be: a favorite if [adding]. Unless [reduceMotion],
     * the heart then fills from the middle while it grows and shrinks back, or dips as it empties.
     */
    fun tap(adding: Boolean, onToggle: (favorite: Boolean) -> Unit) {
        haptics.performHapticFeedback(if (adding) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onToggle(adding)
        if (!reduceMotion.value) {
            // Under way before the next frame, which shows the new state.
            scope.launch(start = CoroutineStart.UNDISPATCHED) { fill.animateFill(adding) }
            scope.launch(start = CoroutineStart.UNDISPATCHED) { scale.animateScale(adding) }
        }
    }
}

/** A heart's [HeartTap], kept while the heart is shown. */
@Composable
fun rememberHeartTap(): HeartTap {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Only Wear's own: Android's Remove animations makes them jump by itself.
    val reduceMotion = rememberUpdatedState(LocalReduceMotion.current)
    return remember(scope, haptics, reduceMotion) { HeartTap(scope, haptics, reduceMotion) }
}

/**
 * The heart itself, the size of [modifier]: outlined in [outlineColor] when the song isn't a
 * favorite ([isFavorite]), filled in the primary color when it is, and moving as a [tap] moves it.
 */
@Composable
fun HeartIcon(isFavorite: Boolean, tap: HeartTap, outlineColor: Color, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val filled = if (isFavorite) 1f else 0f
    // Scaled while drawing, so the animation composes nothing again.
    Box(modifier.graphicsLayer { scaleX = tap.scale.value; scaleY = tap.scale.value }) {
        Icon(
            painterResource(R.drawable.ic_favorite_outline),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            tint = if (isFavorite) primary else outlineColor,
        )
        Icon(
            painterResource(R.drawable.ic_favorite),
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer {
                val shown = if (tap.fill.isRunning) tap.fill.value else filled
                scaleX = shown
                scaleY = shown
            },
            tint = primary,
        )
    }
}

/** A tap's fill: from where it is to full when [adding], or to empty. */
private suspend fun Animatable<Float, *>.animateFill(adding: Boolean) {
    snapTo(if (adding) 0f else 1f)
    animateTo(if (adding) 1f else 0f, tween(if (adding) FillMs else DrainMs, easing = EmphasizedDecelerate))
}

/** A tap's size: growing and back when [adding], or a dip. */
private suspend fun Animatable<Float, *>.animateScale(adding: Boolean) {
    animateTo(
        1f,
        keyframes {
            if (adding) {
                durationMillis = AddMs
                AddPeak at AddMs * 45 / 100 using FastOutSlowInEasing
            } else {
                durationMillis = RemoveMs
                RemoveDip at RemoveMs / 2 using FastOutSlowInEasing
            }
        },
    )
}
