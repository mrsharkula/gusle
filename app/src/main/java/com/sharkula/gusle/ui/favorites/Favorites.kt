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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * fills it from the middle while it grows and shrinks back, removing dips it. With Reduce motion, or
 * when the state changes elsewhere (e.g. the next song, or the tile), it just switches. Null
 * [onToggle] disables it, e.g. with no song.
 */
@Composable
fun FavoriteHeart(
    isFavorite: Boolean,
    onToggle: ((favorite: Boolean) -> Unit)?,
    outlineColor: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Only Wear's own: Android's Remove animations makes them jump by itself.
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    // How filled it is: as it should be, unless a tap's animation is on its way there.
    val filled = if (isFavorite) 1f else 0f
    val fill = remember { Animatable(filled) }
    val scale = remember { Animatable(1f) }
    val label = stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(HeartTouchTarget)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(enabled = onToggle != null, role = Role.Button) {
                val adding = !isFavorite
                haptics.performHapticFeedback(if (adding) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle?.invoke(adding)
                if (!reduceMotion) {
                    // Under way before the next frame, which shows the new state.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { fill.animateFill(adding) }
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { scale.animateScale(adding) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Scaled while drawing, so the animation composes nothing again.
        Box(Modifier.size(iconSize).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
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
                    val shown = if (fill.isRunning) fill.value else filled
                    scaleX = shown
                    scaleY = shown
                },
                tint = primary,
            )
        }
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
