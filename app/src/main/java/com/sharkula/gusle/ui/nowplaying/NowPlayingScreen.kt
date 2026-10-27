package com.sharkula.gusle.ui.nowplaying

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.UnplayableSongs
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.LocalHiddenDepth
import com.sharkula.gusle.ui.components.formatLength
import com.sharkula.gusle.ui.components.onLongPress
import com.sharkula.gusle.ui.components.rememberLengthInWords
import com.sharkula.gusle.ui.components.rememberMayAnimate
import com.sharkula.gusle.ui.components.rememberScreenShown
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.favorites.FavoriteHeart
import com.sharkula.gusle.ui.favorites.rememberFavorites
import com.sharkula.gusle.ui.favorites.rememberSetFavorite
import com.sharkula.gusle.ui.heldWhileHidden
import com.sharkula.gusle.ui.playback.PlayerConnection
import com.sharkula.gusle.ui.playback.rememberShownPlayer
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.unplayableSongs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The player: the playing song's cover in the middle, fading into black, its title, the controls
 * and its artist, and its progress along the edge. It follows the player wherever it's controlled
 * from, e.g. the watch's media controls. Swiping left opens the queue ([onQueue]); tapping the time
 * under the title opens the seek screen ([onSeek]); holding the title, or the time, opens the song's
 * actions ([onSongActions]).
 */
@Composable
fun NowPlayingScreen(
    connection: PlayerConnection,
    songsById: Map<Long, Song>,
    onQueue: () -> Unit,
    onSeek: () -> Unit,
    onSongActions: (Song) -> Unit,
) {
    val player = connection.rememberShownPlayer()

    ScreenScaffold {
        // Just the clock until the player first connects, which takes milliseconds.
        if (player != null) {
            NowPlaying(connection, player, songsById, onQueue, onSeek, onSongActions)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NowPlaying(
    connection: PlayerConnection,
    player: Player,
    songsById: Map<Long, Song>,
    onQueue: () -> Unit,
    onSeek: () -> Unit,
    onSongActions: (Song) -> Unit,
) {
    val item = rememberCurrentMediaItemState(player)
    val playPause = rememberPlayPauseButtonState(player)
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)
    val shuffle = rememberShuffleButtonState(player)
    val shown = rememberScreenShown()
    val animate = rememberMayAnimate(shown)
    val progress = rememberProgress(connection, player, shown)
    val volume = rememberMusicVolume()
    val cantPlay = rememberCantPlay(shown)

    val mediaItem = item.mediaItem
    // Right after a tap, the player knows only the song's ID until the playback service has looked
    // it up, so the title and artist fall back to the song's.
    val song = mediaItem?.songId?.let(songsById::get)
    val metadata = item.mediaMetadata
    // Through the connection rather than the player shown: right after Gusle comes back to the
    // screen, that's the one let go of, and a tap then waits for the new one. Remembered, like the
    // progress below, so a new song doesn't compose the controls and the ring again.
    fun command(enabled: Boolean, action: Player.() -> Unit): (() -> Unit)? =
        if (enabled) ({ connection.withPlayer(action) }) else null
    val onPrevious = remember(connection, previous.isEnabled) { command(previous.isEnabled) { seekToPrevious() } }
    val onPlayPause = remember(connection, playPause.isEnabled) {
        command(playPause.isEnabled) { Util.handlePlayPauseButtonAction(this) }
    }
    val onNext = remember(connection, next.isEnabled) { command(next.isEnabled) { seekToNext() } }
    val songDurationMs by rememberUpdatedState(song?.durationMs ?: 0)
    val elapsedMs = remember(progress) { { progress.currentPositionMs } }
    // Below another screen, e.g. the seek screen, they follow a new song a moment later, as other
    // hidden screens do, from the art that screen read rather than reading the cover twice at once.
    val art = rememberCoverArt(heldWhileHidden(song))
    val favorites = rememberFavorites()
    val setFavorite = rememberSetFavorite()

    NowPlaying(
        // Nothing to play, e.g. when Gusle left the screen before a tap reached the player, and the
        // playback service has started afresh since. The controls are disabled then too.
        title = if (mediaItem == null) {
            stringResource(R.string.nothing_playing)
        } else {
            (metadata.title ?: song?.title)?.toString().orEmpty()
        },
        artist = (metadata.artist ?: song?.artist)?.toString(),
        art = art,
        isPlaying = !playPause.showPlay,
        progress = remember(progress) { { progress.fraction(songDurationMs) } },
        elapsedMs = elapsedMs.takeIf { mediaItem != null },
        animate = animate,
        volume = volume,
        shuffleOn = shuffle.shuffleOn,
        // The song as it's on the watch; none for one deleted while it plays, which disables the heart.
        isFavorite = song != null && song in favorites,
        onFavorite = remember(song, setFavorite) { song?.let { { favorite: Boolean -> setFavorite(it, favorite) } } },
        cantPlay = cantPlay::value,
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onQueue = onQueue,
        onSeek = onSeek,
        // Likewise none for a song deleted while it plays.
        onSongActions = remember(song, onSongActions) { song?.let { { onSongActions(it) } } },
    )
}

/** How long it says a song can't be played after it fails. */
private const val CantPlayMs = 2500L

/**
 * The song that just failed to play (the playback service skipped it, or stopped), to say so for a
 * moment while this screen is [shown] and on top; null otherwise. A failure from just before, e.g.
 * right after the tap that opened it, shows for what's left of that moment.
 */
@Composable
private fun rememberCantPlay(shown: State<Boolean>): State<UnplayableSongs.Failure?> {
    val lastFailure = LocalContext.current.unplayableSongs.lastFailure
    val onTop = LocalScreenIsActive.current
    return produceState<UnplayableSongs.Failure?>(initialValue = null, lastFailure, shown, onTop) {
        combine(snapshotFlow { shown.value && onTop }, lastFailure) { visible, failure -> failure.takeIf { visible } }
            .collectLatest { failure ->
                val left = if (failure == null) 0 else failure.atMs + CantPlayMs - SystemClock.elapsedRealtime()
                value = failure.takeIf { left > 0 }
                if (left > 0) {
                    delay(left)
                    value = null
                }
            }
    }
}

/**
 * The player as drawn in the design, without a player behind it. [progress] is read only while
 * drawing, and [elapsedMs] only by the time under the title, so their ticks redraw the ring and
 * compose that time again, and nothing else.
 */
@Composable
private fun NowPlaying(
    title: String,
    artist: String?,
    /** The cover and its colors; null until the first is ready, when the middle stays empty. */
    art: CoverArt?,
    isPlaying: Boolean,
    progress: () -> Float,
    /** How far the song is, shown under the title; null while there's no song, which shows none. */
    elapsedMs: (() -> Long)?,
    /**
     * Whether anything may move frame by frame: only while Gusle is on the screen, the screen is
     * on and Reduce motion is off. Otherwise everything stands still in its end state.
     */
    animate: Boolean,
    /** Shown along the bottom; the crown changes it. */
    volume: Volume,
    /** Shows a small shuffle icon while the songs play in random order. */
    shuffleOn: Boolean,
    /** Whether the song is a favorite, which the heart along the bottom shows. */
    isFavorite: Boolean,
    /** A tap on the heart, with what it's to be; null while there's no song, which disables it. */
    onFavorite: ((favorite: Boolean) -> Unit)?,
    /** The song that just failed, to say it can't be played; null otherwise. Read only by the message. */
    cantPlay: () -> UnplayableSongs.Failure?,
    /** Null while there's nothing to go back to, which disables the button; likewise the others. */
    onPrevious: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
    onNext: (() -> Unit)?,
    /** A swipe left; also an accessibility action, as screen readers take swipes for themselves. */
    onQueue: () -> Unit,
    /** A tap on the time under the title. */
    onSeek: () -> Unit,
    /** Holding the title, or the time under it; null while there's no song, when holding does nothing. */
    onSongActions: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val crownStepPx = with(LocalDensity.current) { VolumeCrownStep.toPx() }
    val crown = remember(crownStepPx) { CrownSteps(crownStepPx) }
    GusleTheme(colorScheme = art?.colors) {
        DesignScale {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSwipeLeft(onQueue)
                    // The crown changes the volume while this screen is shown.
                    .onRotaryScrollEvent { event ->
                        crown.turn(event.verticalScrollPixels, event.uptimeMillis) { up ->
                            if (volume.step(up)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        true
                    }
                    .requestFocusOnHierarchyActive()
                    // Takes the crown's input without being something to stop at for TalkBack.
                    .focusTarget(),
            ) {
                if (art != null) Cover(art.picture, CoverAlpha, Modifier.align(Alignment.Center))
                ProgressRing(progress, isPlaying, animate, Modifier.fillMaxSize())
                Title(
                    title,
                    animate,
                    onQueue,
                    onSongActions,
                    Modifier.align(Alignment.TopCenter).padding(start = 40.dp, top = 36.dp, end = 40.dp),
                )
                // Before the controls, so where its tap target reaches over them, they take the tap.
                UnderTitle(elapsedMs, cantPlay, onSeek, onSongActions)
                Controls(
                    isPlaying,
                    animate,
                    onPrevious,
                    onPlayPause,
                    onNext,
                    Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
                )
                if (!artist.isNullOrBlank()) {
                    Text(
                        artist,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = 30.dp, top = 158.dp, end = 30.dp)
                            .fillMaxWidth(),
                        color = artistColor(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f),
                    )
                }
                VolumeArc(volume)
                if (shuffleOn) {
                    // Left of the heart, 21 dp left of the middle, as it was of the speaker (6.5 dp
                    // from the heart, which is wider); the heart stays in the middle.
                    ShuffleIcon(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 195.dp)
                            .offset(x = (-21).dp),
                    )
                }
                // Above the volume arc, in the middle, where the speaker was (designs/v2/FAVORITES.md,
                // E). The heart is 16 dp, 194 dp down; its touch target reaches over the arc, which
                // takes no taps.
                FavoriteHeart(
                    isFavorite,
                    onFavorite,
                    outlineColor = artistColor(),
                    iconSize = 16.dp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 178.dp),
                )
            }
        }
    }
}

/**
 * The song's title, centered. One too long for the screen scrolls slowly instead of ending in "…",
 * while [animate] and on the screen: kept below another screen, e.g. the seek screen, it would
 * scroll unseen, and wake Gusle for every frame. It also offers screen readers the Queue
 * ([onQueue]), as they take swipes for themselves. Holding it, anywhere along its line, opens the
 * song's actions ([onSongActions]).
 */
@Composable
private fun Title(
    title: String,
    animate: Boolean,
    onQueue: () -> Unit,
    onSongActions: (() -> Unit)?,
    modifier: Modifier,
) {
    val queueLabel = stringResource(R.string.queue)
    val actionsLabel = stringResource(R.string.open_song_options)
    val haptics = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    Text(
        title,
        modifier = modifier
            .onVisibilityChanged(minFractionVisible = 0f) { visible = it }
            .fillMaxWidth()
            .then(if (onSongActions != null) Modifier.onLongPress(actionsLabel, haptics, onSongActions) else Modifier)
            // A title that fits stays centered.
            .wrapContentWidth()
            .then(if (animate && visible) Modifier.slowMarquee() else Modifier)
            // On the title, the first thing a screen reader reads out.
            .semantics { customActions = listOf(CustomAccessibilityAction(queueLabel) { onQueue(); true }) },
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = designText(17, FontWeight.SemiBold, shadowAlpha = 0.75f),
    )
}

/**
 * Between the title and the controls, over the cover: how far the song is ([elapsedMs]; nothing
 * while it's null), or for a moment instead, that a song [cantPlay]. Never both, as they'd overlap.
 * A failure is read here, so it composes just this again; the time only by [ElapsedTime], so a
 * tick composes just that.
 */
@Composable
private fun BoxScope.UnderTitle(
    elapsedMs: (() -> Long)?,
    cantPlay: () -> UnplayableSongs.Failure?,
    onSeek: () -> Unit,
    onSongActions: (() -> Unit)?,
) {
    val failure = cantPlay()
    if (failure != null) {
        CantPlayMessage(
            failure,
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 30.dp, top = 67.dp, end = 30.dp)
                .fillMaxWidth(),
        )
    } else if (elapsedMs != null && LocalHiddenDepth.current == 0) {
        // Not while kept below another screen, where it would only compose again every second,
        // unseen: it's back, with the time as it is, in the frame it comes into view. The text is
        // 60 dp down, as in the design, in the middle of its tap target.
        // Looked up here rather than at each tick of the time.
        val actionsLabel = stringResource(R.string.open_song_options)
        ElapsedTime(
            elapsedMs,
            onSeek,
            onSongActions,
            actionsLabel,
            Modifier.align(Alignment.TopCenter).padding(top = 45.dp),
        )
    }
}

/**
 * How far the song is, e.g. 1:23, read from [positionMs] only here: a button that opens the seek
 * screen ([onSeek]). The text is small, so the button reaches well around it, over the lower half
 * of the title, which is why holding it opens the song's actions as holding the title does
 * ([onSongActions]); screen readers name that [actionsLabel].
 */
@Composable
private fun ElapsedTime(
    positionMs: () -> Long,
    onSeek: () -> Unit,
    onSongActions: (() -> Unit)?,
    actionsLabel: String,
    modifier: Modifier,
) {
    val position = positionMs()
    val resources = LocalResources.current
    val inWords = rememberLengthInWords()
    Box(
        modifier
            .height(TimeTapHeight)
            .widthIn(min = TimeTapWidth)
            .clip(CircleShape)
            // With the long-press haptic, as a list row's.
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = actionsLabel,
                onLongClick = onSongActions,
                onClick = onSeek,
            )
            // Said in words, as "1:23" could be read as a time of day; worked out only when asked.
            .clearAndSetSemantics { contentDescription = resources.getString(R.string.elapsed_seek, inWords(position)) }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formatLength(position),
            color = artistColor(),
            maxLines = 1,
            style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f),
        )
    }
}

/**
 * Says the song that [failure] is about can't be played, by its title, or that nothing can be
 * played right now when it wasn't the song's fault. In the primary color, and read out by
 * accessibility services when it appears, without interrupting.
 */
@Composable
private fun CantPlayMessage(failure: UnplayableSongs.Failure, modifier: Modifier) {
    Text(
        when {
            !failure.songsFault -> stringResource(R.string.cant_play_now)
            failure.title != null -> stringResource(R.string.cant_play_titled, failure.title)
            else -> stringResource(R.string.cant_play_song)
        },
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = designText(12, FontWeight.Medium, shadowAlpha = 0.85f),
    )
}

/** The cover shows at this opacity, a little dimmed. */
private const val CoverAlpha = 0.9f

/** A swipe left at least this far opens the queue, as does a quicker one of any length. */
private val SwipeDistance = 48.dp
private val SwipeSpeed = 400.dp // per second

/**
 * Calls [onSwipe] when a finger swipes left: far enough, or quickly. Once a drag goes left, it's
 * taken from the controls under it, so they don't take it for a tap. Drags that go right, up or
 * down are left alone, for going back and for the controls, and so is a touch something under it
 * took first, e.g. a long press that opened the song's actions.
 */
private fun Modifier.onSwipeLeft(onSwipe: () -> Unit): Modifier = pointerInput(onSwipe) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        var swiping = false
        while (true) {
            // The main pass, after the controls: what's taken here, they see taken and cancel.
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            // With the samples batched into the event, for a true speed.
            velocity.addPointerInputChange(change)
            val moved = change.position - down.position
            if (!swiping) {
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                // Mostly to the left, or it's not a swipe for the queue.
                if (moved.x > 0 || abs(moved.y) > abs(moved.x)) return@awaitEachGesture
                swiping = true
            }
            if (!change.pressed) {
                // Lifted, rather than cancelled (e.g. the screen going off), which also ends it, taken.
                val swiped = -moved.x >= SwipeDistance.toPx() || -velocity.calculateVelocity().x >= SwipeSpeed.toPx()
                if (change.changedToUp() && swiped) onSwipe()
                change.consume()
                return@awaitEachGesture
            }
            change.consume()
        }
    }
}

/** A small shuffle icon in the artist's color, saying "Shuffle on" to accessibility services. */
@Composable
private fun ShuffleIcon(modifier: Modifier) {
    Icon(
        painterResource(R.drawable.ic_shuffle),
        contentDescription = stringResource(R.string.shuffle_on),
        modifier = modifier.size(13.dp),
        tint = artistColor(),
    )
}

/** Long titles scroll at this speed, pausing this long before each round. */
private val MarqueeSpeed = 30.dp
private const val MarqueePauseMs = 1500

private fun Modifier.slowMarquee() = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = MarqueePauseMs,
    repeatDelayMillis = MarqueePauseMs,
    velocity = MarqueeSpeed,
)

/**
 * Turning the crown as far as would scroll a list by this much changes the volume a notch: on the
 * watch, about four notches per quarter turn.
 */
private val VolumeCrownStep = 24.dp

/** While playing, the played part waves this far in and out, 50 times around a full circle. */
private val WaveHeight = 1.8.dp
private const val WavesPerTurn = 50

/** The waves travel along the ring at 2 radians a second, so each takes π seconds to move one on. */
private const val WaveCycleNanos = (PI * 1_000_000_000).toLong()

/** The wave grows when playing starts, and flattens when it pauses, over this long. */
private const val WaveChangeMs = 300

/**
 * The song's progress along the edge, over a faint track, with a gap at the top for the clock.
 * While playing, the played part waves, and the waves travel along it while [animate] and on the
 * screen.
 */
@Composable
private fun ProgressRing(progress: () -> Float, isPlaying: Boolean, animate: Boolean, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    // Off the screen, e.g. kept below the seek screen or the Queue, the waves would travel unseen,
    // and wake Gusle for every frame.
    var visible by remember { mutableStateOf(false) }
    val travel = animate && visible
    // How wavy, from flat (0) to full height (1), and where the waves are. Both are read only while
    // drawing, so a frame draws just the ring again.
    val wave = remember { Animatable(if (isPlaying) 1f else 0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, travel) {
        val height = if (isPlaying) 1f else 0f
        if (!travel) {
            wave.snapTo(height)
            return@LaunchedEffect
        }
        // Not again when it's already there, e.g. each time the screen comes back on. Undispatched,
        // so it's running before the loop below looks.
        if (wave.value != height) {
            launch(start = CoroutineStart.UNDISPATCHED) { wave.animateTo(height, tween(WaveChangeMs)) }
        }
        // The waves travel while playing, and while flattening after a pause, but only once some of
        // the song is played: until then there's nothing wavy to draw.
        snapshotFlow { progress() > 0f }.collectLatest { played ->
            if (!played) return@collectLatest
            val startPhase = phase.floatValue
            val startNanos = withFrameNanos { it }
            while (isPlaying || wave.isRunning) {
                withFrameNanos { now ->
                    val cycle = ((now - startNanos) % WaveCycleNanos).toFloat() / WaveCycleNanos
                    phase.floatValue = (startPhase + cycle * 2 * PI.toFloat()) % (2 * PI.toFloat())
                }
            }
        }
    }
    // A layer of its own, so a tick or a frame draws just the ring again.
    Spacer(
        modifier.onVisibilityChanged(minFractionVisible = 0f) { visible = it }.graphicsLayer().drawWithCache {
            val center = size.center
            val radius = RingRadius.toPx()
            val topLeft = center - Offset(radius, radius)
            val arcSize = Size(radius * 2, radius * 2)
            val track = Stroke(RingWidth.toPx())
            val played = Stroke(RingWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val waveHeight = WaveHeight.toPx()
            val path = Path()
            onDrawBehind {
                drawArc(RingTrack, RingStart, RingSweep, useCenter = false, topLeft, arcSize, style = track)
                val sweep = RingSweep * progress()
                if (sweep > 0f) {
                    val height = waveHeight * wave.value
                    if (height == 0f) {
                        drawArc(color, RingStart, sweep, useCenter = false, topLeft, arcSize, style = played)
                    } else {
                        path.setWavyArc(center, radius, sweep, height, phase.floatValue)
                        drawPath(path, color, style = played)
                    }
                }
            }
        },
    )
}

/**
 * Sets this path to the ring's played part: [sweep] degrees clockwise from [RingStart], waving
 * [height] px in and out of [radius], with the waves moved along by [phase] radians. It's made of
 * straight steps from [WaveSteps], and allocates nothing, so it can be built every frame.
 */
private fun Path.setWavyArc(center: Offset, radius: Float, sweep: Float, height: Float, phase: Float) {
    rewind()
    val cosPhase = cos(phase)
    val sinPhase = sin(phase)
    val steps = (sweep / WaveSteps.Degrees).toInt().coerceAtMost(WaveSteps.Count)
    for (i in 0..steps) {
        // sin(wave − phase), expanded so the table holds everything that doesn't move.
        val r = radius + height * (WaveSteps.waveSin[i] * cosPhase - WaveSteps.waveCos[i] * sinPhase)
        val x = center.x + r * WaveSteps.x[i]
        val y = center.y + r * WaveSteps.y[i]
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    // The exact end, which usually falls between two steps.
    val rest = sweep - steps * WaveSteps.Degrees
    if (rest > 0f) {
        val along = Math.toRadians(sweep.toDouble()).toFloat()
        val angle = Math.toRadians((RingStart + sweep).toDouble()).toFloat()
        val r = radius + height * sin(along * WavesPerTurn - phase)
        lineTo(center.x + r * cos(angle), center.y + r * sin(angle))
    }
}

/**
 * The ring in steps of half a degree: each step's direction from the center ([x], [y] on a circle
 * of radius 1), and its place in the waves.
 */
private object WaveSteps {
    const val Degrees = 0.5f
    const val Count = (RingSweep / Degrees).toInt()

    val x = FloatArray(Count + 1)
    val y = FloatArray(Count + 1)
    val waveSin = FloatArray(Count + 1)
    val waveCos = FloatArray(Count + 1)

    init {
        for (i in 0..Count) {
            val along = Math.toRadians((i * Degrees).toDouble())
            val angle = Math.toRadians((RingStart + i * Degrees).toDouble())
            x[i] = cos(angle).toFloat()
            y[i] = sin(angle).toFloat()
            waveSin[i] = sin(along * WavesPerTurn).toFloat()
            waveCos[i] = cos(along * WavesPerTurn).toFloat()
        }
    }
}

/** Play is a rounded square while playing, and a pill while paused; it morphs between the two. */
private val PlayingCorner = 18.dp
private val PausedCorner = 26.dp
private const val MorphMs = 350
private val MorphEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Previous, play/pause and next, centered on the screen; a null action disables its button. Play
 * morphs when it changes while [animate], and jumps otherwise.
 */
@Composable
private fun Controls(
    isPlaying: Boolean,
    animate: Boolean,
    onPrevious: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier,
) {
    val skipColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    val corner = remember { Animatable(if (isPlaying) PlayingCorner else PausedCorner, Dp.VectorConverter) }
    LaunchedEffect(isPlaying, animate) {
        val target = if (isPlaying) PlayingCorner else PausedCorner
        // Not again when it's already there, e.g. each time the screen comes back on.
        if (corner.value == target) return@LaunchedEffect
        if (animate) corner.animateTo(target, tween(MorphMs, easing = MorphEasing)) else corner.snapTo(target)
    }
    // A RoundedCornerShape, so the button still morphs it when pressed, too.
    val playShape = remember(corner) { RoundedCornerShape(AnimatedCornerSize { corner.value }) }
    // Media controls keep their direction in right-to-left languages too.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // The skip buttons are 48 dp wide, the smallest touch target, and 4 dp from Play, which
        // keeps their icons where the design's 44 dp buttons 6 dp apart have them.
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious ?: {},
                modifier = Modifier.size(48.dp, 52.dp),
                enabled = onPrevious != null,
                colors = skipColors,
            ) {
                ShadowedIcon(R.drawable.ic_skip_previous, stringResource(R.string.previous_song), SkipShadow)
            }
            FilledIconButton(
                onClick = onPlayPause ?: {},
                modifier = Modifier.size(66.dp, 52.dp),
                enabled = onPlayPause != null,
                shapes = IconButtonDefaults.shapes(playShape),
                colors = seeThroughButtonColors(),
            ) {
                ShadowedIcon(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    stringResource(if (isPlaying) R.string.pause else R.string.play),
                    SeeThroughIconShadow,
                )
            }
            IconButton(
                onClick = onNext ?: {},
                modifier = Modifier.size(48.dp, 52.dp),
                enabled = onNext != null,
                colors = skipColors,
            ) {
                ShadowedIcon(R.drawable.ic_skip_next, stringResource(R.string.next_song), SkipShadow)
            }
        }
    }
}

/**
 * A corner of [size], read only when the shape's outline is made: a change draws the shape again
 * without recomposing anything.
 */
@Stable
private class AnimatedCornerSize(private val size: () -> Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = with(density) { size().toPx() }
}

private val SkipShadow = Shadow(radius = 4.dp, offset = DpOffset(0.dp, 1.dp), alpha = 0.85f)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NowPlayingPreview() {
    NowPlayingPreview(
        title = "River at Dawn",
        artist = "Test Ensemble",
        art = rememberStandInArt(),
        isPlaying = true,
        shuffleOn = true,
        isFavorite = true,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NowPlayingNoCoverPreview() {
    NowPlayingPreview(
        title = "A Very Long Song Title That Runs Past the Edge",
        artist = "Test Trio",
        art = CoverArt(null, null, null),
        isPlaying = false,
        shuffleOn = false,
        isFavorite = false,
    )
}

@Composable
private fun NowPlayingPreview(
    title: String,
    artist: String?,
    art: CoverArt,
    isPlaying: Boolean,
    shuffleOn: Boolean,
    isFavorite: Boolean,
) {
    GusleTheme {
        AppScaffold {
            ScreenScaffold {
                NowPlaying(
                    title,
                    artist,
                    art,
                    isPlaying,
                    // 1:25 of 3:45.
                    progress = { 0.38f },
                    elapsedMs = { 85_000 },
                    animate = false,
                    volume = PreviewVolume,
                    shuffleOn = shuffleOn,
                    isFavorite = isFavorite,
                    onFavorite = {},
                    cantPlay = { null },
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onQueue = {},
                    onSeek = {},
                    onSongActions = {},
                )
            }
        }
    }
}

private object PreviewVolume : Volume {
    override val fraction = 0.6f
    override val notches = 15
    override val canChange = true

    override fun step(up: Boolean) = true

    override fun set(fraction: Float) {}
}
