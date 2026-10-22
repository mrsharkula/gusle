package com.sharkula.gusle.ui.nowplaying

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.songId
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Seeking in the playing song: where it is, big, and along the edge. Turning the crown moves
 * through the song, and it jumps there half a second after the crown stops; −10 s and +10 s jump at
 * once. In the song's colors, over its cover, dimmed. It follows the player like Now playing, also
 * on to the next song, and stays open after a jump, so the song can be nudged again. Holding the
 * title opens the song's actions ([onSongActions]), as on Now playing.
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
    val shown = rememberScreenShown()
    val progress = rememberProgress(connection, player, shown)
    val hint = rememberHint(shown, rememberMayAnimateState(shown))

    val mediaItem = item.mediaItem
    // As on Now playing, the song's title until the playback service has looked it up.
    val song = mediaItem?.songId?.let(songsById::get)
    val songDurationMs by rememberUpdatedState(song?.durationMs ?: 0)
    val art = rememberCoverArt(song)
    val positionMs = remember(progress) { { progress.currentPositionMs } }
    val durationMs = remember(progress) { { progress.durationOr(songDurationMs) } }

    // Each song afresh. Closing the screen before the song has jumped jumps at once, but not in the
    // next song: a song change drops a jump still to come.
    val scope = rememberCoroutineScope()
    val seek = remember(progress, mediaItem?.mediaId) {
        CrownSeek(mediaItem?.mediaId, connection, scope, positionMs, durationMs)
    }
    DisposableEffect(seek) {
        onDispose { seek.close() }
    }
    // Nothing to seek in while there's no song, or while its length isn't known.
    val canSeek = mediaItem != null && durationMs() > 0
    val onBack = remember(seek, canSeek) { if (canSeek) ({ seek.skip(-SkipMs) }) else null }
    val onForward = remember(seek, canSeek) { if (canSeek) ({ seek.skip(SkipMs) }) else null }

    Seek(
        title = if (mediaItem == null) {
            stringResource(R.string.nothing_playing)
        } else {
            (item.mediaMetadata.title ?: song?.title)?.toString().orEmpty()
        },
        art = art,
        positionMs = positionMs,
        landingMs = remember(seek) { { seek.landingMs } },
        durationMs = durationMs,
        hintAlpha = remember(hint) { { hint.value } },
        onCrownStep = remember(seek) { seek::step },
        onBack = onBack,
        onForward = onForward,
        // None for a song deleted while it plays.
        onSongActions = remember(song, onSongActions) { song?.let { { onSongActions(it) } } },
    )
}

/** The buttons jump this far back and on. */
private const val SkipMs = 10_000L

/** The song jumps this long after the crown's last step. */
private const val JumpDelayMs = 500L

/** After a jump, the landing point stays up until the position is this close to it, or this long. */
private const val CloseEnoughMs = 1_500L
private const val CatchUpMs = 1_000L

/**
 * Where the crown takes the song with [mediaId]: each step a little on or back ([SeekSteps]) from
 * where it was taking it, or from where the music is ([positionMs]), in a song [durationMs] long.
 * Half a second after the last step, the song jumps there; turned back to where it started, it
 * doesn't. Read [landingMs] only where it's shown, so a step composes or draws just that again.
 */
@Stable
private class CrownSeek(
    private val mediaId: String?,
    private val connection: PlayerConnection,
    private val scope: CoroutineScope,
    private val positionMs: () -> Long,
    private val durationMs: () -> Long,
) {
    /** Where the song is about to jump; null while it plays on where it is. */
    var landingMs: Long? by mutableStateOf(null)
        private set

    private val steps = SeekSteps()
    private var jump: Job? = null

    /** Where the turn of the crown started, until the song jumps. */
    private var turnFromMs: Long? = null

    /** Where the song is about to jump, until the jump is sent to the player. */
    private var unsentMs: Long? = null

    /** A step of the crown, [forward] or back, at [timeMs]. False at either end, where nothing moves. */
    fun step(forward: Boolean, timeMs: Long): Boolean {
        val duration = durationMs()
        if (duration <= 0) return false
        val from = landingMs ?: positionMs()
        val to = steps.next(from, forward, timeMs, duration)
        val turnFrom = turnFromMs ?: from.also { turnFromMs = it }
        when {
            // Turned on and back again: the music plays on where it is, rather than jumping back
            // by as long as the turn took.
            to == turnFrom -> drop()
            // At an end the crown may still be turning: the jump waits until it stops, rather than
            // ending the song while the next one would take the rest of the turn.
            to != from || landingMs != null -> jumpTo(to, afterMs = JumpDelayMs)
        }
        return to != from
    }

    /** Jumps [byMs] on, or back if it's negative, at once: from where the crown was taking the song, if anywhere. */
    fun skip(byMs: Long) {
        val duration = durationMs()
        if (duration <= 0) return
        jumpTo(((landingMs ?: positionMs()) + byMs).coerceIn(0, duration), afterMs = 0)
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
    }

    private fun jumpTo(to: Long, afterMs: Long) {
        landingMs = to
        unsentMs = to
        jump?.cancel()
        jump = scope.launch {
            delay(afterMs)
            turnFromMs = null
            unsentMs = null
            connection.withPlayer { seekInSong(to) }
            // The position follows a moment later: until then the landing point stays up, rather
            // than the old position for a moment.
            withTimeoutOrNull(CatchUpMs) { snapshotFlow { abs(positionMs() - to) < CloseEnoughMs }.first { it } }
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

/** A step of the crown moves the song this far. */
private const val SeekStepMs = 5_000L

/**
 * Steps of the crown closer together than these move it twice and four times as far: turned
 * quickly, a whole song takes a couple of turns rather than fifty steps.
 */
private const val QuickStepMs = 80L
private const val QuickerStepMs = 40L

/**
 * Where steps of the crown take a song: [SeekStepMs] on or back each, or more when they come
 * quickly ([stepsWorth]); never before its start or past its end.
 */
internal class SeekSteps {
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
        val by = SeekStepMs * steps
        return (if (forward) fromMs + by else fromMs - by).coerceIn(0, durationMs.coerceAtLeast(0))
    }
}

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
 * crown composes the times again and redraws the ring, and nothing else.
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
    /** How long the song is; 0 while that isn't known. */
    durationMs: () -> Long,
    /** How much the hint shows, from 0 to 1; read only by the hint. */
    hintAlpha: () -> Float,
    /** A step of the crown, [forward] or back, at [timeMs]; whether it moved anything. */
    onCrownStep: (forward: Boolean, timeMs: Long) -> Boolean,
    /** Null while there's no song, or its length isn't known, which disables the buttons. */
    onBack: (() -> Unit)?,
    onForward: (() -> Unit)?,
    /** Holding the title; null while there's no song, when holding does nothing. */
    onSongActions: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val actionsLabel = stringResource(R.string.open_song_options)
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
                SeekRing(positionMs, landingMs, durationMs, Modifier.fillMaxSize())
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
                // Its digits' middle level with the buttons', 106 dp down (the design's 76 dp put them
                // 6.5 dp higher), and the length under it as far below as in the design.
                BigTime(positionMs, landingMs, durationMs, Modifier.align(Alignment.TopCenter).padding(top = 82.5.dp))
                OfLength(positionMs, landingMs, durationMs, Modifier.align(Alignment.TopCenter).padding(top = 130.5.dp))
                // Back on the left and on to the right, also in right-to-left languages, like the
                // controls on Now playing.
                SkipButton(
                    R.drawable.ic_replay_10,
                    stringResource(R.string.back_10_seconds),
                    onBack,
                    Modifier.align(AbsoluteAlignment.TopLeft).absolutePadding(left = 22.dp, top = 86.dp),
                )
                SkipButton(
                    R.drawable.ic_forward_10,
                    stringResource(R.string.forward_10_seconds),
                    onForward,
                    Modifier.align(AbsoluteAlignment.TopRight).absolutePadding(top = 86.dp, right = 22.dp),
                )
                // Where the volume is on Now playing.
                CrownHint(hintAlpha, Modifier.align(Alignment.TopCenter).padding(top = 178.dp))
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

/** A change shorter than this isn't shown: the landing point is where the music is. */
private const val MinChangeMs = 1_000L

/**
 * Under the big time: the song's length, "of 3:45", and while the crown takes the song elsewhere,
 * first how far, in the primary color: "+0:42 · of 3:45". Screen readers get the length with the
 * big time.
 */
@Composable
private fun OfLength(positionMs: () -> Long, landingMs: () -> Long?, durationMs: () -> Long, modifier: Modifier) {
    val duration = durationMs()
    if (duration <= 0) return
    val of = stringResource(R.string.of_length, formatLength(duration))
    // The position only while turning, so a tick doesn't compose this again otherwise.
    val change = landingMs()?.let { it - positionMs() }?.takeIf { abs(it) >= MinChangeMs }
    val changeColor = MaterialTheme.colorScheme.primary
    Text(
        if (change == null) {
            AnnotatedString(of)
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(color = changeColor, fontWeight = FontWeight.SemiBold)) {
                    append(if (change < 0) '−' else '+')
                    append(formatLength(abs(change)))
                }
                append(" · ")
                append(of)
            }
        },
        modifier = modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = designText(13, FontWeight.Normal, shadowAlpha = 0.85f).copy(fontFeatureSettings = "tnum"),
    )
}

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

/** A watch crown seen from the side: a rounded outline with two ridges across it. */
@Composable
private fun CrownGlyph(color: Color) {
    Canvas(Modifier.size(7.dp, 12.dp)) {
        val line = 1.5.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(line / 2, line / 2),
            size = Size(size.width - line, size.height - line),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(line),
        )
        for (y in listOf(5.25.dp, 8.25.dp)) {
            drawLine(color, Offset(2.5.dp.toPx(), y.toPx()), Offset(size.width - 2.5.dp.toPx(), y.toPx()), line)
        }
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
 * is there, the way to it is fainter, and a thin white mark stays where the music is. Everything is
 * read only while drawing, so a tick or a step of the crown draws just the ring again.
 */
@Composable
private fun SeekRing(positionMs: () -> Long, landingMs: () -> Long?, durationMs: () -> Long, modifier: Modifier) {
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
                if (landing != null && abs(landing - position) >= MinChangeMs) {
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

@Composable
private fun SeekPreview(landingMs: Long?, hintAlpha: Float) {
    GusleTheme {
        AppScaffold {
            ScreenScaffold {
                Seek(
                    title = "River at Dawn",
                    art = rememberStandInArt(),
                    positionMs = { 83_000 },
                    landingMs = { landingMs },
                    durationMs = { 225_000 },
                    hintAlpha = { hintAlpha },
                    onCrownStep = { _, _ -> true },
                    onBack = {},
                    onForward = {},
                    onSongActions = {},
                )
            }
        }
    }
}
