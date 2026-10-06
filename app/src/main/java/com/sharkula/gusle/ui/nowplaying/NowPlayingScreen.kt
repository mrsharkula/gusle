package com.sharkula.gusle.ui.nowplaying

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberCurrentMediaItemState
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.LocalPlatformContext
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.covers.NoCoverPainter
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.playback.PlayerConnection
import com.sharkula.gusle.ui.playback.rememberShownPlayer
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

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
    val progress = rememberProgress(connection, player)

    val mediaItem = item.mediaItem
    // Right after a tap, the player knows only the song's ID until the playback service has looked
    // it up, so the title and artist fall back to the song's.
    val song = mediaItem?.songId?.let(songsById::get)
    val metadata = item.mediaMetadata
    // Through the connection rather than the player shown: right after Gusle comes back to the
    // screen, that's the one let go of, and a tap then waits for the new one.
    fun command(enabled: Boolean, action: Player.() -> Unit): (() -> Unit)? =
        if (enabled) ({ connection.withPlayer(action) }) else null
    NowPlaying(
        // Nothing to play, e.g. when Gusle left the screen before a tap reached the player, and the
        // playback service has started afresh since. The controls are disabled then too.
        title = if (mediaItem == null) {
            stringResource(R.string.nothing_playing)
        } else {
            (metadata.title ?: song?.title)?.toString().orEmpty()
        },
        artist = (metadata.artist ?: song?.artist)?.toString(),
        cover = song?.let(::SongCover),
        isPlaying = !playPause.showPlay,
        progress = { progress.fraction() },
        onPrevious = command(previous.isEnabled) { seekToPrevious() },
        onPlayPause = command(playPause.isEnabled) { Util.handlePlayPauseButtonAction(this) },
        onNext = command(next.isEnabled) { seekToNext() },
    )
}

/** Light on the battery, and still smooth: a step moves the ring a few pixels. */
private const val ProgressTickMs = 1000L

/**
 * The [player]'s progress, updated once a second while it plays, but only while it's connected,
 * Gusle is on the screen and the screen is on: media3's rememberProgressStateWithTickInterval goes
 * on ticking in the background.
 */
@OptIn(UnstableApi::class)
@Composable
private fun rememberProgress(connection: PlayerConnection, player: Player): ProgressStateWithTickInterval {
    val scope = rememberCoroutineScope()
    val progress = remember(player) { ProgressStateWithTickInterval(player, ProgressTickMs, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    LaunchedEffect(progress, lifecycle, context, connection) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A player let go of reads 0 for everything; its connected successor takes over soon.
            combine(context.screenOn(), snapshotFlow { connection.isConnected(player) }) { on, connected ->
                on && connected
            }.collectLatest { live -> if (live) progress.observe() }
        }
    }
    return progress
}

@OptIn(UnstableApi::class)
private fun ProgressStateWithTickInterval.fraction(): Float =
    if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

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
    cover: SongCover?,
    isPlaying: Boolean,
    progress: () -> Float,
    /** Null while there's nothing to go back to, which disables the button; likewise the others. */
    onPrevious: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Scales every dp and sp below with the screen, so they're the design's own numbers.
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
        ) {
            Box(Modifier.fillMaxSize()) {
                Cover(cover, Modifier.align(Alignment.Center))
                ProgressRing(progress, Modifier.fillMaxSize())
                Text(
                    title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 40.dp, top = 36.dp, end = 40.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = designText(17, FontWeight.SemiBold, shadowAlpha = 0.75f),
                )
                Controls(
                    isPlaying,
                    onPrevious,
                    onPlayPause,
                    onNext,
                    Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
                )
                if (!artist.isNullOrBlank()) {
                    val colors = MaterialTheme.colorScheme
                    Text(
                        artist,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = 30.dp, top = 158.dp, end = 30.dp)
                            .fillMaxWidth(),
                        // Halfway to onSurface, like the design: onSurfaceVariant alone is too dark
                        // over a cover.
                        color = lerp(colors.onSurfaceVariant, colors.onSurface, 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f),
                    )
                }
                // The bottom stays empty for the volume arc.
            }
        }
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

/**
 * The cover in a circle in the middle, or the no-cover note, fading into black toward the edge.
 * Center-cropped, so a 16:9 cover shows its middle.
 */
@kotlin.OptIn(ExperimentalCoilApi::class)
@Composable
private fun Cover(cover: SongCover?, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(cover, context) {
        ImageRequest.Builder(context)
            .data(cover)
            // Shows the last song's cover until this one's is ready, rather than blinking black.
            .useExistingImageAsPlaceholder(true)
            .build()
    }
    val noCover = rememberNoCoverPainter()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.size(161.dp).fadeIntoBlack(),
        // Also when there's no cover to load at all.
        error = noCover,
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

/** A warm glow with a large faint music note. */
@Composable
private fun rememberNoCoverPainter(): Painter {
    val note = painterResource(R.drawable.ic_music_note)
    val colors = MaterialTheme.colorScheme
    return remember(note, colors) {
        val glow = Brush.radialGradient(0f to colors.primaryContainer, 0.7f to NoCoverGlowEdge, 1f to Color.Black)
        NoCoverPainter(note, glow, colors.primary.copy(alpha = 0.22f), noteSize = 104.dp)
    }
}

/** The ring runs clockwise from just right of the clock (0° is 3 o'clock) to just left of it. */
private const val RingStart = -70f
private const val RingSweep = 320f

/** The song's progress along the edge, over a faint track, with a gap at the top for the clock. */
@Composable
private fun ProgressRing(progress: () -> Float, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    // A layer of its own, so a tick draws just the ring again.
    Canvas(modifier.graphicsLayer()) {
        val radius = 112.dp.toPx()
        val topLeft = center - Offset(radius, radius)
        val arcSize = Size(radius * 2, radius * 2)
        val width = 4.dp.toPx()
        drawArc(RingTrack, RingStart, RingSweep, useCenter = false, topLeft, arcSize, style = Stroke(width))
        val fraction = progress()
        if (fraction > 0f) {
            drawArc(
                color,
                RingStart,
                RingSweep * fraction,
                useCenter = false,
                topLeft,
                arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
    }
}

private val RingTrack = Color.White.copy(alpha = 0.16f)

/** Previous, play/pause and next, centered on the screen; a null action disables its button. */
@Composable
private fun Controls(
    isPlaying: Boolean,
    onPrevious: (() -> Unit)?,
    onPlayPause: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier,
) {
    val skipColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
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
                // A rounded square while playing, a pill while paused.
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(if (isPlaying) 18.dp else 26.dp)),
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
    NowPlayingPreview(
        title = "River at Dawn",
        artist = "Test Ensemble",
        cover = SongCover(Uri.EMPTY, 0),
        isPlaying = true,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NowPlayingNoCoverPreview() {
    NowPlayingPreview(
        title = "A Very Long Song Title That Runs Past the Edge",
        artist = "Test Trio",
        cover = null,
        isPlaying = false,
    )
}

@kotlin.OptIn(ExperimentalCoilApi::class)
@Composable
private fun NowPlayingPreview(title: String, artist: String?, cover: SongCover?, isPlaying: Boolean) {
    // A 16:9 stand-in cover, in the Yamato cover's red, fading to near black.
    val previewCover = AsyncImagePreviewHandler {
        createBitmap(320, 180).applyCanvas {
            val gradient = LinearGradient(
                0f, 0f, 320f, 180f, 0xFF93291C.toInt(), 0xFF101010.toInt(), Shader.TileMode.CLAMP,
            )
            drawPaint(Paint().apply { shader = gradient })
        }.asImage()
    }
    GusleTheme {
        CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewCover) {
            AppScaffold {
                ScreenScaffold {
                    NowPlaying(title, artist, cover, isPlaying, progress = { 0.38f }, {}, {}, {})
                }
            }
        }
    }
}
