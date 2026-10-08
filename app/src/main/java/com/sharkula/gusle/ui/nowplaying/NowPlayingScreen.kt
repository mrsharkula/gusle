package com.sharkula.gusle.ui.nowplaying

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
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
import com.sharkula.gusle.coverColors
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.components.rememberMayAnimate
import com.sharkula.gusle.ui.components.rememberScreenShown
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.covers.NoCoverPainter
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.coverColorScheme
import com.sharkula.gusle.ui.playback.PlayerConnection
import com.sharkula.gusle.ui.playback.rememberShownPlayer
import com.sharkula.gusle.ui.theme.DefaultColorScheme
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The player: the playing song's cover in the middle, fading into black, its title, the controls
 * and its artist, and its progress along the edge. It follows the player wherever it's controlled
 * from, e.g. the watch's media controls.
 */
@Composable
fun NowPlayingScreen(connection: PlayerConnection, songsById: Map<Long, Song>) {
    val player = connection.rememberShownPlayer()

    ScreenScaffold {
        // Just the clock until the player first connects, which takes milliseconds.
        if (player != null) {
            NowPlaying(connection, player, songsById)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NowPlaying(connection: PlayerConnection, player: Player, songsById: Map<Long, Song>) {
    val item = rememberCurrentMediaItemState(player)
    val playPause = rememberPlayPauseButtonState(player)
    val previous = rememberPreviousButtonState(player)
    val next = rememberNextButtonState(player)
    val shuffle = rememberShuffleButtonState(player)
    val shown = rememberScreenShown()
    val animate = rememberMayAnimate(shown)
    val progress = rememberProgress(connection, player, shown)
    val volume = rememberMusicVolume()

    val mediaItem = item.mediaItem
    // Right after a tap, the player knows only the song's ID until the playback service has looked
    // it up, so the title and artist fall back to the song's.
    val song = mediaItem?.songId?.let(songsById::get)
    val metadata = item.mediaMetadata
    // Through the connection rather than the player shown: right after Gusle comes back to the
    // screen, that's the one let go of, and a tap then waits for the new one.
    fun command(enabled: Boolean, action: Player.() -> Unit): (() -> Unit)? =
        if (enabled) ({ connection.withPlayer(action) }) else null

    // The song's cover and its colors go up together once both are ready; until then the last
    // ones stay. Opened again on the song shown last, they're up in the first frame.
    val cover = song?.let(::SongCover)
    val coverColors = LocalContext.current.coverColors
    val coverSizePx = coverSizePx()
    val art by produceState(coverColors.lastArt?.takeIf { it.cover == cover }, cover, coverSizePx) {
        value = coverColors.art(cover, coverSizePx)
    }

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
        progress = { progress.fraction(song?.durationMs ?: 0) },
        animate = animate,
        volume = volume,
        shuffleOn = shuffle.shuffleOn,
        onPrevious = command(previous.isEnabled) { seekToPrevious() },
        onPlayPause = command(playPause.isEnabled) { Util.handlePlayPauseButtonAction(this) },
        onNext = command(next.isEnabled) { seekToNext() },
    )
}

/** Light on the battery, and still smooth: a step moves the ring a few pixels. */
private const val ProgressTickMs = 1000L

/**
 * The [player]'s progress, updated once a second while it plays, but only while it's connected
 * and [shown]: media3's rememberProgressStateWithTickInterval goes on ticking in the background.
 */
@OptIn(UnstableApi::class)
@Composable
private fun rememberProgress(
    connection: PlayerConnection,
    player: Player,
    shown: State<Boolean>,
): ProgressStateWithTickInterval {
    val scope = rememberCoroutineScope()
    val progress = remember(player) { ProgressStateWithTickInterval(player, ProgressTickMs, scope) }
    LaunchedEffect(progress, connection, shown) {
        // A player let go of reads 0 for everything; its connected successor takes over soon.
        snapshotFlow { shown.value && connection.isConnected(player) }
            .collectLatest { live -> if (live) progress.observe() }
    }
    return progress
}

/**
 * How far it is through the song. Until the song is prepared, e.g. when the player comes back to
 * it paused, the player doesn't know its length yet, so it's [songDurationMs] (MediaStore's).
 */
@OptIn(UnstableApi::class)
private fun ProgressStateWithTickInterval.fraction(songDurationMs: Long): Float {
    val duration = durationMs.takeIf { it > 0 } ?: songDurationMs
    return if (duration > 0) (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** The design is drawn for a screen this many dp across: the OnePlus Watch 4's. */
private const val DesignWidth = 233f

/**
 * The player as drawn in the design, without a player behind it. [progress] is read only while
 * drawing, so its ticks redraw the ring and nothing else.
 */
@Composable
private fun NowPlaying(
    title: String,
    artist: String?,
    /** The cover and its colors; null until the first is ready, when the middle stays empty. */
    art: CoverArt?,
    isPlaying: Boolean,
    progress: () -> Float,
    /**
     * Whether anything may move frame by frame: only while Gusle is on the screen, the screen is
     * on and Reduce motion is off. Otherwise everything stands still in its end state.
     */
    animate: Boolean,
    /** Shown along the bottom; the crown changes it. */
    volume: Volume,
    /** Shows a small shuffle icon while the songs play in random order. */
    shuffleOn: Boolean,
    /** Null while there's nothing to go back to, which disables the button; likewise the others. */
    onPrevious: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val crownStepPx = with(LocalDensity.current) { CrownStep.toPx() }
    val crown = remember(crownStepPx) { CrownSteps(crownStepPx) }
    GusleTheme(colorScheme = art?.colors ?: DefaultColorScheme) {
        DesignScale {
            Box(
                Modifier
                    .fillMaxSize()
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
                if (art != null) Cover(art.picture, Modifier.align(Alignment.Center))
                ProgressRing(progress, isPlaying, animate, Modifier.fillMaxSize())
                Text(
                    title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 40.dp, top = 36.dp, end = 40.dp)
                        .fillMaxWidth()
                        // A title that fits stays centered.
                        .wrapContentWidth()
                        // One that doesn't scrolls slowly instead of ending in "…".
                        .then(if (animate) Modifier.slowMarquee() else Modifier),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = designText(17, FontWeight.SemiBold, shadowAlpha = 0.75f),
                )
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
                VolumeArc(volume, iconColor = artistColor())
                if (shuffleOn) {
                    // Left of the speaker, 8 dp apart; the speaker stays in the middle.
                    ShuffleIcon(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 195.dp)
                            .offset(x = (-21).dp),
                    )
                }
            }
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
private val CrownStep = 24.dp

/**
 * The artist's color, also for the small icons near it: halfway from onSurfaceVariant to
 * onSurface, like the design, as onSurfaceVariant alone is too dark over a cover.
 */
@Composable
private fun artistColor(): Color {
    val colors = MaterialTheme.colorScheme
    return lerp(colors.onSurfaceVariant, colors.onSurface, 0.5f)
}

/** Scales every dp and sp in [content] with the screen, so they're the design's own numbers. */
@Composable
private fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth.value / DesignWidth
        val screenDensity = LocalDensity.current
        val screenViewConfiguration = LocalViewConfiguration.current
        val designDensity = remember(screenDensity, scale) { DesignDensity(screenDensity, scale) }
        val designViewConfiguration = remember(screenViewConfiguration, scale) {
            DesignViewConfiguration(screenViewConfiguration, scale)
        }
        CompositionLocalProvider(
            LocalDensity provides designDensity,
            LocalViewConfiguration provides designViewConfiguration,
            content = content,
        )
    }
}

/**
 * The screen's density with dp and sp [scale] times as large. Sp keep the system's font scaling,
 * which on Android 14+ grows small text more than large text.
 */
private class DesignDensity(private val screen: Density, scale: Float) : Density {
    override val density = screen.density * scale
    override val fontScale = screen.fontScale

    override fun TextUnit.toDp(): Dp = with(screen) { this@toDp.toDp() }

    override fun Dp.toSp(): TextUnit = with(screen) { this@toSp.toSp() }
}

/** Keeps touch targets at least as large as on any screen, when [DesignDensity] shrinks dp. */
private class DesignViewConfiguration(screen: ViewConfiguration, scale: Float) : ViewConfiguration by screen {
    override val minimumTouchTargetSize = screen.minimumTouchTargetSize / scale
}

/**
 * The design's text: the watch's font at [size] sp, in a line half as tall again, centered in it
 * as in the design, with a soft dark shadow that keeps it readable over a cover.
 */
@Composable
private fun designText(size: Int, weight: FontWeight, shadowAlpha: Float): TextStyle {
    val shadowOffset = with(LocalDensity.current) { 1.dp.toPx() }
    val shadowBlur = with(LocalDensity.current) { 6.dp.toPx() }
    return MaterialTheme.typography.bodyMedium.copy(
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = (size * 1.5f).sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        shadow = TextShadow(Color.Black.copy(alpha = shadowAlpha), Offset(0f, shadowOffset), shadowBlur),
    )
}

/** The cover is drawn this large (dp of the design's [DesignWidth]). */
private const val CoverDiameter = 161f

/** The cover's size on this screen, in pixels. */
@Composable
private fun coverSizePx(): Int =
    (LocalWindowInfo.current.containerSize.width * CoverDiameter / DesignWidth).roundToInt()

/**
 * The [picture] in a circle in the middle, or the no-cover note when there's none, fading into
 * black toward the edge. Center-cropped, so a 16:9 cover shows its middle.
 */
@Composable
private fun Cover(picture: ImageBitmap?, modifier: Modifier) {
    val painter = if (picture != null) remember(picture) { BitmapPainter(picture) } else rememberNoCoverPainter()
    Image(
        painter,
        contentDescription = null,
        modifier = modifier.size(CoverDiameter.dp).fadeIntoBlack(),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Fades the content out from 35% of the radius to nothing at the edge, at 90% opacity. It's drawn
 * once into a layer of its own, and only drawn again when the content changes.
 */
private fun Modifier.fadeIntoBlack() =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        alpha = 0.9f
    }.drawWithCache {
        val fade = Brush.radialGradient(0.35f to Color.Black, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawRect(fade, blendMode = BlendMode.DstIn)
        }
    }

/** The design's darker sand between primaryContainer in the middle and black at the edge. */
private val NoCoverGlowEdge = Color(0xFF2B2217)

/**
 * A warm glow with a large faint music note, always in sand: that's a song without a cover's color,
 * also while the last song's colors are still up.
 */
@Composable
private fun rememberNoCoverPainter(): Painter {
    val note = painterResource(R.drawable.ic_music_note)
    return remember(note) {
        val colors = DefaultColorScheme
        val glow = Brush.radialGradient(0f to colors.primaryContainer, 0.7f to NoCoverGlowEdge, 1f to Color.Black)
        NoCoverPainter(note, glow, colors.primary.copy(alpha = 0.22f), noteSize = 104.dp)
    }
}

/** The ring runs clockwise from just right of the clock (0° is 3 o'clock) to just left of it. */
private const val RingStart = -70f
private const val RingSweep = 320f

/** While playing, the played part waves this far in and out, 50 times around a full circle. */
private val WaveHeight = 1.8.dp
private const val WavesPerTurn = 50

/** The waves travel along the ring at 2 radians a second, so each takes π seconds to move one on. */
private const val WaveCycleNanos = (PI * 1_000_000_000).toLong()

/** The wave grows when playing starts, and flattens when it pauses, over this long. */
private const val WaveChangeMs = 300

/**
 * The song's progress along the edge, over a faint track, with a gap at the top for the clock.
 * While playing, the played part waves, and the waves travel along it while [animate].
 */
@Composable
private fun ProgressRing(progress: () -> Float, isPlaying: Boolean, animate: Boolean, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    // How wavy, from flat (0) to full height (1), and where the waves are. Both are read only while
    // drawing, so a frame draws just the ring again.
    val wave = remember { Animatable(if (isPlaying) 1f else 0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, animate) {
        val height = if (isPlaying) 1f else 0f
        if (!animate) {
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
        modifier.graphicsLayer().drawWithCache {
            val center = size.center
            val radius = 112.dp.toPx()
            val topLeft = center - Offset(radius, radius)
            val arcSize = Size(radius * 2, radius * 2)
            val track = Stroke(4.dp.toPx())
            val played = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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

private val RingTrack = Color.White.copy(alpha = 0.16f)

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
            // See-through: the cover shows through a tint of the primary color.
            FilledIconButton(
                onClick = onPlayPause ?: {},
                modifier = Modifier.size(66.dp, 52.dp),
                enabled = onPlayPause != null,
                shapes = IconButtonDefaults.shapes(playShape),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    contentColor = Color.White,
                ),
            ) {
                ShadowedIcon(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    stringResource(if (isPlaying) R.string.pause else R.string.play),
                    PlayShadow,
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
private val PlayShadow = Shadow(radius = 3.dp, offset = DpOffset(0.dp, 1.dp), alpha = 0.65f)

/** A 28 dp icon in the content color, over a soft [shadow] of its own shape. */
@Composable
private fun ShadowedIcon(@DrawableRes id: Int, contentDescription: String, shadow: Shadow) {
    val icon = ImageVector.vectorResource(id)
    val outline = remember(icon) { IconOutline(icon) }
    Icon(icon, contentDescription, Modifier.size(28.dp).dropShadow(outline, shadow))
}

/**
 * The shape of a vector icon, for its shadow: all its paths, moved, rotated and scaled by their
 * groups as when drawn. Compose blurs it once into a bitmap and keeps it.
 */
private class IconOutline(icon: ImageVector) : Shape {
    private val path = Path().apply { addGroup(icon.root, Matrix()) }
    private val viewport = Size(icon.viewportWidth, icon.viewportHeight)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val scaled = Path().apply {
            addPath(path)
            transform(Matrix().apply { scale(size.width / viewport.width, size.height / viewport.height) })
        }
        return Outline.Generic(scaled)
    }
}

/** Adds [group]'s paths, transformed as VectorPainter draws them, inside [parent]'s transformation. */
private fun Path.addGroup(group: VectorGroup, parent: Matrix) {
    val matrix = Matrix(parent.values.copyOf()).apply {
        translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        rotateZ(group.rotation)
        scale(group.scaleX, group.scaleY)
        translate(-group.pivotX, -group.pivotY)
    }
    for (node in group) {
        when (node) {
            is VectorPath -> addPath(node.pathData.toPath().apply { transform(matrix) })
            is VectorGroup -> addGroup(node, matrix)
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NowPlayingPreview() {
    // A 16:9 stand-in cover, in the Yamato cover's red, fading to near black.
    val picture = remember {
        createBitmap(320, 180).applyCanvas {
            val gradient = LinearGradient(
                0f, 0f, 320f, 180f, 0xFF93291C.toInt(), 0xFF101010.toInt(), Shader.TileMode.CLAMP,
            )
            drawPaint(Paint().apply { shader = gradient })
        }.asImageBitmap()
    }
    NowPlayingPreview(
        title = "River at Dawn",
        artist = "Test Ensemble",
        art = CoverArt(SongCover(Uri.EMPTY, 0), picture, coverColorScheme(0xFF93291C.toInt())),
        isPlaying = true,
        shuffleOn = true,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NowPlayingNoCoverPreview() {
    NowPlayingPreview(
        title = "A Very Long Song Title That Runs Past the Edge",
        artist = "Test Trio",
        art = CoverArt(null, null, DefaultColorScheme),
        isPlaying = false,
        shuffleOn = false,
    )
}

@Composable
private fun NowPlayingPreview(title: String, artist: String?, art: CoverArt, isPlaying: Boolean, shuffleOn: Boolean) {
    GusleTheme {
        AppScaffold {
            ScreenScaffold {
                NowPlaying(
                    title,
                    artist,
                    art,
                    isPlaying,
                    progress = { 0.38f },
                    animate = false,
                    volume = PreviewVolume,
                    shuffleOn = shuffleOn,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
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
