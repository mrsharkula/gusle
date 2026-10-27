package com.sharkula.gusle.ui.nowplaying

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.lengthShownChoice
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.LocalHiddenDepth
import com.sharkula.gusle.ui.components.formatLength
import com.sharkula.gusle.ui.components.onLongPress
import com.sharkula.gusle.ui.components.rememberLengthInWords
import com.sharkula.gusle.ui.components.rememberMayAnimateState
import com.sharkula.gusle.ui.components.rememberScreenShown
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.playback.PlayerConnection
import com.sharkula.gusle.ui.playback.rememberShownPlayer
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Seeking in the playing song: where it is, big, and along the edge. Turning the crown moves
 * through the song, by the step chosen in Settings ([CrownStep]), and it jumps there half a second
 * after the crown stops; the − and + buttons jump at once, by their own step ([JumpStep]). In the
 * song's colors, over its cover, dimmed. The song's length is the small time at the bottom; a tap
 * on it shows how much is left instead, and back, which is remembered ([LengthShownChoice]). It
 * follows the player like Now playing, also on to the next song, and stays open after a jump, so
 * the song can be nudged again. Holding the title opens the song's actions ([onSongActions]), as
 * on Now playing.
 */
@Composable
fun SeekScreen(connection: PlayerConnection, songsById: Map<Long, Song>, onSongActions: (Song) -> Unit) {
    val player = connection.rememberShownPlayer()

    ScreenScaffold {
        // Just the clock until the player first connects, which takes milliseconds.
        if (player != null) {
            Seek(connection, player, songsById, onSongActions)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Seek(
    connection: PlayerConnection,
    player: Player,
    songsById: Map<Long, Song>,
    onSongActions: (Song) -> Unit,
) {
    val item = rememberCurrentMediaItemState(player)
    val screenShown = rememberScreenShown()
    // Nor while another screen is over it, e.g. the song's actions: nothing ticks for it there. One
    // state object throughout, as the hint and the progress keep the one they're given: a screen
    // restored under others would otherwise keep a "hidden" one for good, and its hint never fade.
    val hidden by rememberUpdatedState(LocalHiddenDepth.current != 0)
    val shown = remember(screenShown) { derivedStateOf { screenShown.value && !hidden } }
    val progress = rememberProgress(connection, player, shown)
    val hint = rememberHint(shown, rememberMayAnimateState(shown))

    val mediaItem = item.mediaItem
    // As on Now playing, the song's title until the playback service has looked it up.
    val song = mediaItem?.songId?.let(songsById::get)
    val songDurationMs by rememberUpdatedState(song?.durationMs ?: 0)
    val art = rememberCoverArt(song)
    val positionMs = remember(progress) { { progress.currentPositionMs } }
    val livePositionMs = remember(player) { { player.currentPosition } }
    val durationMs = remember(progress) { { progress.durationOr(songDurationMs) } }

    // Each song afresh. Closing the screen before the song has jumped jumps at once, but not in the
    // next song: a song change drops a jump still to come. The crown's step can't change while this
    // screen is open (Settings is elsewhere), so it needn't survive one.
    val scope = rememberCoroutineScope()
    val crownStep = LocalCrownStep.current
    val seek = remember(progress, mediaItem?.mediaId, crownStep) {
        CrownSeek(mediaItem?.mediaId, connection, scope, livePositionMs, positionMs, durationMs, crownStep.ms)
    }
    DisposableEffect(seek) {
        onDispose { seek.close() }
    }
    // Nothing to seek in while there's no song, or while its length isn't known.
    val canSeek = mediaItem != null && durationMs() > 0
    val step = LocalJumpStep.current
    val onBack = remember(seek, canSeek, step) { if (canSeek) ({ seek.skip(-step.ms) }) else null }
    val onForward = remember(seek, canSeek, step) { if (canSeek) ({ seek.skip(step.ms) }) else null }
    // Read from disk as Gusle starts, so it's there by now, bar a restore in the very first frames.
    val lengthShown = LocalContext.current.lengthShownChoice

    Seek(
        title = if (mediaItem == null) {
            stringResource(R.string.nothing_playing)
        } else {
            (item.mediaMetadata.title ?: song?.title)?.toString().orEmpty()
        },
        art = art,
        positionMs = positionMs,
        landingMs = remember(seek) { { seek.landingMs } },
        changeMs = remember(seek) { { seek.changeMs } },
        durationMs = durationMs,
        hintAlpha = remember(hint) { { hint.value } },
        lengthShown = remember(lengthShown) { { lengthShown.current } },
        onLengthShown = remember(lengthShown) { lengthShown::choose },
        onCrownStep = remember(seek) { seek::step },
        step = step,
        onBack = onBack,
        onForward = onForward,
        // None for a song deleted while it plays.
        onSongActions = remember(song, onSongActions) { song?.let { { onSongActions(it) } } },
    )
}

/** The song jumps this long after the crown's last step. */
private const val JumpDelayMs = 500L

/** After a jump, the landing point stays up until the position shown moves, or this long. */
private const val CatchUpMs = 1_000L

/**
 * Where the crown takes the song with [mediaId]: each step [stepMs] on or back ([SeekSteps]), or
 * more when they come quickly, from where it was taking it, or from where the music really is
 * ([livePositionMs]), in a song [durationMs] long. Not from the position shown ([shownPositionMs]):
 * that ticks once a second, so it can lag the music by most of a second, a whole step of 1 s. Half
 * a second after the last step, the song jumps there; turned back to where it started, it doesn't.
 * Read [landingMs] and [changeMs] only where they're shown, so a step composes or draws just that
 * again.
 */
@Stable
private class CrownSeek(
    private val mediaId: String?,
    private val connection: PlayerConnection,
    private val scope: CoroutineScope,
    /** Where the music is, straight from the player. */
    private val livePositionMs: () -> Long,
    /** Where the music is as shown; state, which follows a jump a moment later. */
    private val shownPositionMs: () -> Long,
    private val durationMs: () -> Long,
    stepMs: Long,
) {
    /** Where the song is about to jump; null while it plays on where it is. */
    var landingMs: Long? by mutableStateOf(null)
        private set

    /** How far the crown has taken the song from where the turn started, until it jumps; null otherwise. */
    var changeMs: Long? by mutableStateOf(null)
        private set

    private val steps = SeekSteps(stepMs)
    private var jump: Job? = null

    /** Where the turn of the crown started, until the song jumps. */
    private var turnFromMs: Long? = null

    /** Where the song is about to jump, until the jump is sent to the player. */
    private var unsentMs: Long? = null

    /** A step of the crown, [forward] or back, at [timeMs]. False at either end, where nothing moves. */
    fun step(forward: Boolean, timeMs: Long): Boolean {
        val duration = durationMs()
        if (duration <= 0) return false
        val from = landingMs ?: livePositionMs()
        val to = steps.next(from, forward, timeMs, duration)
        val turnFrom = turnFromMs ?: from.also { turnFromMs = it }
        when {
            // Turned on and back again: the music plays on where it is, rather than jumping back
            // by as long as the turn took.
            to == turnFrom -> drop()
            // At an end the crown may still be turning: the jump waits until it stops, rather than
            // ending the song while the next one would take the rest of the turn.
            to != from || landingMs != null -> jumpTo(to, afterMs = JumpDelayMs, change = to - turnFrom)
        }
        return to != from
    }

    /** Jumps [byMs] on, or back if it's negative, at once: from where the crown was taking the song, if anywhere. */
    fun skip(byMs: Long) {
        val duration = durationMs()
        if (duration <= 0) return
        jumpTo(((landingMs ?: livePositionMs()) + byMs).coerceIn(0, duration), afterMs = 0, change = null)
    }

    /**
     * Sends a jump still to come at once, e.g. as the screen closes before the crown's half second
     * is up; not once the player has moved on to another song, or Gusle has let go of it.
     */
    fun close() {
        jump?.cancel()
        val to = unsentMs ?: return
        unsentMs = null
        connection.player.value?.seekInSong(to)
    }

    private fun drop() {
        jump?.cancel()
        turnFromMs = null
        unsentMs = null
        landingMs = null
        changeMs = null
    }

    /** [change] is how far the turn has taken the song, to show; none for a jump at once. */
    private fun jumpTo(to: Long, afterMs: Long, change: Long?) {
        landingMs = to
        changeMs = change
        unsentMs = to
        jump?.cancel()
        jump = scope.launch {
            delay(afterMs)
            turnFromMs = null
            unsentMs = null
            changeMs = null
            connection.withPlayer { seekInSong(to) }
            // The position shown follows a moment later, at its next tick or on the player's word:
            // until it moves, the landing point stays up, rather than the old position for a moment.
            // (Not until it's close to the landing point: the old position is already that close
            // after a step of a second.)
            withTimeoutOrNull(CatchUpMs) { snapshotFlow { shownPositionMs() }.drop(1).first() }
            landingMs = null
        }
    }

    /**
     * Jumps to [to] in this song; not in the next one, if the player has just moved on. By the
     * song's index, as a song only restored, not yet prepared, can't be sought in otherwise: Media3
     * doesn't know yet that it can be. The player starts it there once it plays.
     */
    private fun Player.seekInSong(to: Long) {
        if (currentMediaItem?.mediaId == mediaId) seekTo(currentMediaItemIndex, to)
    }
}

/**
 * Steps of the crown closer together than these move it twice and four times as far: turned
 * quickly, a whole song takes a couple of turns rather than fifty steps.
 */
private const val QuickStepMs = 80L
private const val QuickerStepMs = 40L

/**
 * Where steps of the crown take a song: [stepMs] on or back each (the [CrownStep] chosen in
 * Settings), or more when they come quickly ([stepsWorth]); never before its start or past its end.
 */
internal class SeekSteps(private val stepMs: Long) {
    private var lastStepMs = 0L
    private var lastForward: Boolean? = null
    private var lastWorth = 1

    /** Where a step [forward] or back at [timeMs] takes a song [durationMs] long from [fromMs]. */
    fun next(fromMs: Long, forward: Boolean, timeMs: Long, durationMs: Long): Long {
        val steps = when {
            // Turning the other way starts slowly again.
            forward != lastForward -> 1
            // Steps the crown reports at once go as far as the first of them: it's how quickly it
            // turns that counts, not how much it reports at a time.
            timeMs == lastStepMs -> lastWorth
            else -> stepsWorth(timeMs - lastStepMs)
        }
        lastStepMs = timeMs
        lastForward = forward
        lastWorth = steps
        val by = stepMs * steps
        return (if (forward) fromMs + by else fromMs - by).coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

/**
 * How much of a song [durationMs] long is left at [atMs], in whole seconds as the times are shown:
 * each cut to its second first, so the big time and the time left add up to the length shown
 * (3:55 and −0:52 of 4:47, not −0:51 from the milliseconds between). Never below zero.
 */
internal fun timeLeftMs(durationMs: Long, atMs: Long): Long = ((durationMs / 1000 - atMs / 1000) * 1000).coerceAtLeast(0)

/** How many steps' worth a step of the crown [sinceLastMs] after the one before moves. */
internal fun stepsWorth(sinceLastMs: Long): Int = when {
    sinceLastMs < QuickerStepMs -> 4
    sinceLastMs < QuickStepMs -> 2
    else -> 1
}

/** The hint shows this long once the screen is on, then fades out over this long. */
private const val HintMs = 3_000L
private const val HintFadeMs = 300

/**
 * How much the hint shows: fully at first, for [HintMs] once the screen is [shown], then it fades
 * out, or goes at once while nothing may move ([mayAnimate]). Each time the screen opens, also when
 * it's only restored on top, e.g. after a font size change: simpler, and a reminder then does no
 * harm.
 */
@Composable
private fun rememberHint(shown: State<Boolean>, mayAnimate: State<Boolean>): State<Float> {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(alpha) {
        snapshotFlow { shown.value }.first { it }
        delay(HintMs)
        if (mayAnimate.value) alpha.animateTo(0f, tween(HintFadeMs)) else alpha.snapTo(0f)
    }
    return alpha.asState()
}

/**
 * Turning the crown as far as would scroll a list by this much moves the song a step: as for the
 * volume on Now playing.
 */
private val SeekCrownStep = 24.dp

/**
 * The seek screen as drawn in the design, without a player behind it. [positionMs], [landingMs] and
 * [durationMs] are read only by the times and while drawing the ring, so a tick or a step of the
 * crown composes the times again and redraws the ring, and nothing else; the length at the bottom
 * composes again only when it changes, unless it's showing what's left.
 */
@Composable
private fun Seek(
    title: String,
    /** The cover and its colors; null until the first is ready, when the middle stays empty. */
    art: CoverArt?,
    /** Where the music is. */
    positionMs: () -> Long,
    /** Where the crown is taking it; null while it plays on where it is. */
    landingMs: () -> Long?,
    /** How far the crown has taken it from where the turn started; null while it isn't turning. */
    changeMs: () -> Long?,
    /** How long the song is; 0 while that isn't known. */
    durationMs: () -> Long,
    /** How much the hint shows, from 0 to 1; read only by the hint. */
    hintAlpha: () -> Float,
    /** What the small time at the bottom shows, the length or what's left; read only by it. Null until it's read from disk, when there's nothing there yet. */
    lengthShown: () -> LengthShown?,
    /** A tap on the small time: show that one instead. */
    onLengthShown: (LengthShown) -> Unit,
    /** A step of the crown, [forward] or back, at [timeMs]; whether it moved anything. */
    onCrownStep: (forward: Boolean, timeMs: Long) -> Boolean,
    /** How far the buttons jump, which their icons and labels say. */
    step: JumpStep,
    /** Null while there's no song, or its length isn't known, which disables the buttons. */
    onBack: (() -> Unit)?,
    onForward: (() -> Unit)?,
    /** Holding the title; null while there's no song, when holding does nothing. */
    onSongActions: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val actionsLabel = stringResource(R.string.open_song_options)
    val resources = LocalResources.current
    val crownStepPx = with(LocalDensity.current) { SeekCrownStep.toPx() }
    val crown = remember(crownStepPx) { CrownSteps(crownStepPx) }
    GusleTheme(colorScheme = art?.colors) {
        DesignScale {
            Box(
                Modifier
                    .fillMaxSize()
                    // The crown moves through the song while this screen is shown.
                    .onRotaryScrollEvent { event ->
                        crown.turn(event.verticalScrollPixels, event.uptimeMillis) { forward ->
                            if (onCrownStep(forward, event.uptimeMillis)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        true
                    }
                    .requestFocusOnHierarchyActive()
                    // Takes the crown's input without being something to stop at for TalkBack.
                    .focusTarget(),
            ) {
                if (art != null) Cover(art.picture, DimmedCoverAlpha, Modifier.align(Alignment.Center))
                SeekRing(positionMs, landingMs, changeMs, durationMs, Modifier.fillMaxSize())
                Text(
                    title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 44.dp, top = 34.dp, end = 44.dp)
                        .fillMaxWidth()
                        .then(if (onSongActions != null) Modifier.onLongPress(actionsLabel, haptics, onSongActions) else Modifier),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = designText(13, FontWeight.SemiBold, shadowAlpha = 0.75f),
                )
                // Its digits' middle on the screen's middle line, 116.5 dp down, level with the
                // buttons' (the design's 76 dp put them 17 dp higher), and while the crown turns,
                // how far, 48 dp under its top: where the length was until 2026-10-27.
                BigTime(positionMs, landingMs, durationMs, Modifier.align(Alignment.TopCenter).padding(top = 93.dp))
                Change(changeMs, Modifier.align(Alignment.TopCenter).padding(top = 141.dp))
                // Centered on the middle line, 22 dp in from each side. Back on the left and on to
                // the right, also in right-to-left languages, like the controls on Now playing.
                SkipButton(
                    step.backIcon,
                    step.backLabel(resources),
                    onBack,
                    Modifier.align(AbsoluteAlignment.CenterLeft).absolutePadding(left = 22.dp),
                )
                SkipButton(
                    step.forwardIcon,
                    step.forwardLabel(resources),
                    onForward,
                    Modifier.align(AbsoluteAlignment.CenterRight).absolutePadding(right = 22.dp),
                )
                // The song's length, or what's left of it, where the volume is on Now playing: its
                // text in the middle of its tap target, on the line the hint had.
                SongLength(
                    lengthShown,
                    onLengthShown,
                    positionMs,
                    landingMs,
                    durationMs,
                    Modifier.align(Alignment.TopCenter).padding(top = LengthLineMiddle - TimeTapHeight / 2),
                )
                // Under it, in the ring's bottom pocket, for its few seconds.
                CrownHint(hintAlpha, Modifier.align(Alignment.TopCenter).padding(top = 197.dp))
            }
        }
    }
}

/**
 * Where the song is, big: where the crown is taking it while it turns ([landingMs]), otherwise
 * where the music is ([positionMs]). Screen readers read it in words, with the song's length, when
 * they come to it, and not as it changes, which would be every second.
 */
@Composable
private fun BigTime(positionMs: () -> Long, landingMs: () -> Long?, durationMs: () -> Long, modifier: Modifier) {
    val at = landingMs() ?: positionMs()
    val duration = durationMs()
    val resources = LocalResources.current
    val inWords = rememberLengthInWords()
    Text(
        formatLength(at),
        // Worked out only when asked.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (duration > 0) {
                resources.getString(R.string.position_of_length, inWords(at), inWords(duration))
            } else {
                inWords(at)
            }
        },
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = designText(42, FontWeight(650), shadowAlpha = 0.8f, shadowOffset = 2.dp, shadowBlur = 10.dp)
            .copy(lineHeight = 42.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    )
}

/**
 * Under the big time, while the crown takes the song elsewhere: how far ([changeMs]), in the
 * primary color, "+0:42"; nothing otherwise. Composed again as that changes, not every tick. Only
 * for the eyes: screen readers get the landing point with the big time.
 */
@Composable
private fun Change(changeMs: () -> Long?, modifier: Modifier) {
    val change = changeMs()?.takeIf { it != 0L } ?: return
    Text(
        buildString {
            append(if (change < 0) '−' else '+')
            append(formatLength(abs(change)))
        },
        modifier = modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = designText(13, FontWeight.SemiBold, shadowAlpha = 0.85f).copy(fontFeatureSettings = "tnum"),
    )
}

/**
 * At the bottom: the song's length, "4:47", or how much of it is left, "−1:34", as [lengthShown]
 * says; a button, and a tap shows the other ([onLengthShown]). The length composes again only when
 * it changes; what's left counts down with the music, or with the crown while it turns
 * ([landingMs]), as the big time does, so that composes every second. Nothing while the length
 * isn't known, nor until what to show is read from disk. The text is small, so the button
 * reaches well around it, as the time's on Now playing does. Screen readers hear it in words,
 * which of the two it is, and what a tap does; a tap's switch is announced as its state changing.
 */
@Composable
private fun SongLength(
    lengthShown: () -> LengthShown?,
    onLengthShown: (LengthShown) -> Unit,
    positionMs: () -> Long,
    landingMs: () -> Long?,
    durationMs: () -> Long,
    modifier: Modifier,
) {
    val duration = durationMs()
    if (duration <= 0) return
    val shown = lengthShown() ?: return
    val resources = LocalResources.current
    val inWords = rememberLengthInWords()
    val ms = when (shown) {
        LengthShown.Length -> duration
        LengthShown.TimeLeft -> timeLeftMs(duration, landingMs() ?: positionMs())
    }
    val tapLabel = stringResource(if (shown == LengthShown.Length) R.string.show_time_left else R.string.show_length)
    Box(
        modifier
            .height(TimeTapHeight)
            .widthIn(min = TimeTapWidth)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = tapLabel) { onLengthShown(shown.other) }
            // Said in words, as "4:47" could be read as a time of day; worked out only when asked.
            .clearAndSetSemantics {
                contentDescription = resources.getString(
                    if (shown == LengthShown.Length) R.string.length_long else R.string.time_left_words,
                    inWords(ms),
                )
                // Which it is, as its state, so a tap's switch is announced: a new description alone isn't.
                stateDescription = resources.getString(if (shown == LengthShown.Length) R.string.length_state else R.string.time_left_state)
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (shown == LengthShown.Length) formatLength(ms) else stringResource(R.string.time_left, formatLength(ms)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            style = designText(13, FontWeight.Normal, shadowAlpha = 0.85f).copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** The middle of the length's line in the design: 13 sp in a line half as tall again, 178 dp down. */
private val LengthLineMiddle = 187.75.dp

/** A see-through round button with an [icon] that says what it does ([label]); disabled while [onClick] is null. */
@Composable
private fun SkipButton(@DrawableRes icon: Int, label: String, onClick: (() -> Unit)?, modifier: Modifier) {
    FilledIconButton(
        onClick = onClick ?: {},
        modifier = modifier.size(40.dp),
        enabled = onClick != null,
        colors = seeThroughButtonColors(),
    ) {
        ShadowedIcon(icon, label, SeeThroughIconShadow, size = 24.dp)
    }
}

/**
 * "Turn the crown", with a small crown, while [alpha] (read only while drawing) is above 0; then
 * nothing. It's only for the eyes: screen readers move with the crown, and have the buttons.
 */
@Composable
private fun CrownHint(alpha: () -> Float, modifier: Modifier) {
    val visible by remember { derivedStateOf { alpha() > 0f } }
    if (!visible) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha() }
            // Takes the taps on it, so one doesn't reach the length's button under it and switch that.
            .pointerInput(Unit) { detectTapGestures { } }
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrownGlyph(color)
        Text(
            stringResource(R.string.turn_the_crown),
            color = color,
            maxLines = 1,
            style = designText(11, FontWeight.Normal, shadowAlpha = 0.85f),
        )
    }
}

/** A watch crown seen from the side, 7 × 12 dp: the Settings row's icon ([R.drawable.ic_crown]). */
@Composable
private fun CrownGlyph(color: Color) {
    // The crown is 16 of the icon's 24 units tall: at 18 dp it's 12 dp, spilling over this box
    // evenly all round, so the row lays out just the crown.
    Box(Modifier.size(7.dp, 12.dp), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_crown), contentDescription = null, Modifier.requiredSize(18.dp), tint = color)
    }
}

/** The knob on the ring, and its dark outline. */
private val KnobRadius = 5.5.dp
private val KnobOutlineWidth = 1.5.dp
private val KnobOutline = Color.Black.copy(alpha = 0.6f)

/** The way from the music to where the crown takes it is this faint. */
private const val AheadAlpha = 0.55f

/** The mark where the music is reaches this far in and out of the ring, this wide, over a dark edge. */
private val MarkReach = 7.dp
private val MarkWidth = 2.5.dp
private val MarkEdgeWidth = 1.dp
private val MarkEdge = Color.Black.copy(alpha = 0.5f)

/**
 * The song along the edge, like Now playing's ring, but always flat: played up to where the music
 * is ([positionMs]), with a knob there. While the crown takes it elsewhere ([landingMs]), the knob
 * is there, the way to it is fainter, and until it jumps ([changeMs]) a thin white mark stays where
 * the music is. Everything is read only while drawing, so a tick or a step of the crown draws just
 * the ring again.
 */
@Composable
private fun SeekRing(
    positionMs: () -> Long,
    landingMs: () -> Long?,
    changeMs: () -> Long?,
    durationMs: () -> Long,
    modifier: Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    // A layer of its own, so a tick or a step draws just the ring again.
    Spacer(
        modifier.graphicsLayer().drawWithCache {
            val center = size.center
            val radius = RingRadius.toPx()
            val topLeft = center - Offset(radius, radius)
            val arcSize = Size(radius * 2, radius * 2)
            val track = Stroke(RingWidth.toPx())
            val played = Stroke(RingWidth.toPx(), cap = StrokeCap.Round)
            val ahead = color.copy(alpha = AheadAlpha)
            val knobRadius = KnobRadius.toPx()
            val knobOutline = Stroke(KnobOutlineWidth.toPx())
            val markReach = MarkReach.toPx()
            val markWidth = MarkWidth.toPx()
            val markEdgeWidth = markWidth + 2 * MarkEdgeWidth.toPx()
            onDrawBehind {
                drawArc(RingTrack, RingStart, RingSweep, useCenter = false, topLeft, arcSize, style = track)
                val duration = durationMs()
                if (duration <= 0) return@onDrawBehind
                val position = positionMs()
                val landing = landingMs()
                val music = sweepTo(position, duration)
                val knob = sweepTo(landing ?: position, duration)
                // Played up to the music, or to the knob when it's further back; fainter from there
                // to the other.
                val near = minOf(music, knob)
                val far = maxOf(music, knob)
                if (near > 0f) drawArc(color, RingStart, near, useCenter = false, topLeft, arcSize, style = played)
                if (far > near) drawArc(ahead, RingStart + near, far - near, useCenter = false, topLeft, arcSize, style = played)
                if (landing != null && changeMs() != null) {
                    val way = direction(music)
                    val inner = center + way * (radius - markReach)
                    val outer = center + way * (radius + markReach)
                    drawLine(MarkEdge, inner, outer, markEdgeWidth, cap = StrokeCap.Round)
                    drawLine(Color.White, inner, outer, markWidth, cap = StrokeCap.Round)
                }
                val knobAt = center + direction(knob) * radius
                drawCircle(color, knobRadius, knobAt)
                drawCircle(KnobOutline, knobRadius, knobAt, style = knobOutline)
            }
        },
    )
}

/** How far along the ring [ms] into a song [durationMs] long is, in degrees from [RingStart]. */
private fun sweepTo(ms: Long, durationMs: Long): Float = RingSweep * (ms.toFloat() / durationMs).coerceIn(0f, 1f)

/** The way out from the middle to the ring, [sweep] degrees along it. */
private fun direction(sweep: Float): Offset {
    val angle = Math.toRadians((RingStart + sweep).toDouble())
    return Offset(cos(angle).toFloat(), sin(angle).toFloat())
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SeekPreview() {
    // Just opened: 1:23 of 3:45, with the hint.
    SeekPreview(landingMs = null, hintAlpha = 1f)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SeekTurningPreview() {
    // Turning on to 2:05, the music still at 1:23.
    SeekPreview(landingMs = 125_000, hintAlpha = 1f)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SeekFiveSecondsPreview() {
    SeekPreview(landingMs = null, hintAlpha = 0f, step = JumpStep.Five)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SeekFifteenSecondsPreview() {
    // Its icons are Gusle's own, drawn from Material's 5 and 10.
    SeekPreview(landingMs = null, hintAlpha = 0f, step = JumpStep.Fifteen)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SeekTimeLeftPreview() {
    // The length tapped: 2:22 left of 3:45.
    SeekPreview(landingMs = null, hintAlpha = 0f, lengthShown = LengthShown.TimeLeft)
}

@Composable
private fun SeekPreview(
    landingMs: Long?,
    hintAlpha: Float,
    step: JumpStep = JumpStep.Default,
    lengthShown: LengthShown = LengthShown.Length,
) {
    GusleTheme {
        AppScaffold {
            ScreenScaffold {
                Seek(
                    title = "River at Dawn",
                    art = rememberStandInArt(),
                    positionMs = { 83_000 },
                    landingMs = { landingMs },
                    changeMs = { landingMs?.let { it - 83_000 } },
                    durationMs = { 225_000 },
                    hintAlpha = { hintAlpha },
                    lengthShown = { lengthShown },
                    onLengthShown = {},
                    onCrownStep = { _, _ -> true },
                    step = step,
                    onBack = {},
                    onForward = {},
                    onSongActions = {},
                )
            }
        }
    }
}
