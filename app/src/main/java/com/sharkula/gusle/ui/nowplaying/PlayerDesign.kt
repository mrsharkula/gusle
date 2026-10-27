package com.sharkula.gusle.ui.nowplaying

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonColors
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.R
import com.sharkula.gusle.coverColors
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.covers.CoverColors
import com.sharkula.gusle.ui.covers.NoCoverPainter
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.theme.LocalAppTheme
import com.sharkula.gusle.ui.theme.seedColorScheme
import kotlin.math.roundToInt

// What Now playing and the seek screen share: they're drawn alike, in the playing song's colors
// over its cover.

/** The design is drawn for a screen this many dp across: the OnePlus Watch 4's. */
private const val DesignWidth = 233f

/** Scales every dp and sp in [content] with the screen, so they're the design's own numbers. */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
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
 * The design's text: the watch's font at [size] sp, in a line [lineHeight] tall (half as tall again
 * unless given), centered in it as in the design, with a soft dark shadow that keeps it readable
 * over a cover, [shadowOffset] down and blurred over [shadowBlur]; none at [shadowAlpha] 0.
 */
@Composable
fun designText(
    size: Int,
    weight: FontWeight,
    shadowAlpha: Float,
    shadowOffset: Dp = 1.dp,
    shadowBlur: Dp = 6.dp,
    lineHeight: TextUnit = (size * 1.5f).sp,
): TextStyle {
    val offset = with(LocalDensity.current) { shadowOffset.toPx() }
    val blur = with(LocalDensity.current) { shadowBlur.toPx() }
    return MaterialTheme.typography.bodyMedium.copy(
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        shadow = if (shadowAlpha > 0f) TextShadow(Color.Black.copy(alpha = shadowAlpha), Offset(0f, offset), blur) else null,
    )
}

/**
 * The artist's color, also for the small things near it: halfway from onSurfaceVariant to
 * onSurface, like the design, as onSurfaceVariant alone is too dark over a cover.
 */
@Composable
fun artistColor(): Color {
    val colors = MaterialTheme.colorScheme
    return lerp(colors.onSurfaceVariant, colors.onSurface, 0.5f)
}

/** The cover is drawn this large (dp of the design's [DesignWidth]). */
private const val CoverDiameter = 161f

/**
 * The cover's size in pixels on a screen [screenWidthPx] across. The tile reads it at this size
 * too, from the same width, so one copy in Coil's memory, and one read of the file, serves both.
 */
fun coverSizePx(screenWidthPx: Int): Int = (screenWidthPx * CoverDiameter / DesignWidth).roundToInt()

/** The cover's size on this screen, in pixels: the window's, which on a watch is the whole screen. */
@Composable
private fun coverSizePx(): Int = coverSizePx(LocalWindowInfo.current.containerSize.width)

/**
 * The [song]'s cover and its colors, which go up together once both are ready; until then the
 * last ones stay. When they're the ones loaded last, e.g. by Now playing as the seek screen opens
 * on its song, they're up in the first frame, and the cover isn't read again. Null until the first
 * are ready.
 */
@Composable
fun rememberCoverArt(song: Song?): CoverArt? = rememberArt(song, CoverColors::art)

/**
 * The [song]'s cover alone, for a screen in other colors than the song's own, e.g. the playing
 * song's: its colors aren't worked out ([CoverColors.pictureOnly]). Like [rememberCoverArt], in the
 * first frame when it's the one loaded last, e.g. by Now playing on the same song; otherwise once
 * it's read: at once from Coil's memory when a screen or the tile showed it lately, or within a
 * second on the watch. Null until then. With no [song] (null), art without a picture. As the song
 * changes, the art read last stays until the new one is there.
 */
@Composable
fun rememberCoverPicture(song: Song?): CoverArt? = rememberArt(song, CoverColors::pictureOnly)

/**
 * The [song]'s cover as [read] reads it, at the cover's size: the art loaded last when it's the same
 * song's, at once, otherwise once it's read. Null until then.
 */
@Composable
private fun rememberArt(
    song: Song?,
    read: suspend CoverColors.(cover: SongCover?, sizePx: Int) -> CoverArt,
): CoverArt? {
    val cover = song?.let(::SongCover)
    val coverColors = LocalContext.current.coverColors
    val coverSizePx = coverSizePx()
    // Not a cover that couldn't be read: that one is tried again.
    val loaded = remember(cover, coverSizePx) {
        coverColors.lastArt?.takeIf { it.cover == cover && (it.picture != null || cover == null) }
    }
    val art by produceState(loaded, cover, coverSizePx) {
        value = loaded ?: coverColors.read(cover, coverSizePx)
    }
    return loaded ?: art
}

/**
 * The [picture] in a circle in the middle, or the no-cover note when there's none, fading into
 * black toward the edge, at [alpha] opacity. Center-cropped, so a 16:9 cover shows its middle. The
 * note glows in [noCoverColors]: the app theme's, which are a song without a cover's own; another
 * [noCoverIcon] can stand in for it, e.g. a playlist's.
 */
@Composable
fun Cover(
    picture: ImageBitmap?,
    alpha: Float,
    modifier: Modifier,
    noCoverColors: ColorScheme = LocalAppTheme.current.colors,
    @DrawableRes noCoverIcon: Int = R.drawable.ic_music_note,
) {
    val painter = if (picture != null) {
        remember(picture) { BitmapPainter(picture) }
    } else {
        rememberNoCoverPainter(noCoverColors, noCoverIcon)
    }
    Image(
        painter,
        contentDescription = null,
        modifier = modifier.size(CoverDiameter.dp).fadeIntoBlack(alpha),
        contentScale = ContentScale.Crop,
    )
}

/**
 * Fades the content out from 35% of the radius to nothing at the edge, at [alpha] opacity. It's
 * drawn once into a layer of its own, and only drawn again when the content changes.
 */
private fun Modifier.fadeIntoBlack(alpha: Float) =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        this.alpha = alpha
    }.drawWithCache {
        val fade = Brush.radialGradient(0.35f to Color.Black, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawRect(fade, blendMode = BlendMode.DstIn)
        }
    }

/**
 * How bright the no-cover glow is, 70% of the way out, next to primaryContainer in the middle: the
 * design's darker shade between it and black at the edge.
 */
private const val NoCoverGlowEdgeBrightness = 0.58f

/**
 * A glow with a large faint music note ([icon]), in [colors]: on Now playing the app theme's, which
 * are a song without a cover's, also while the last song's colors are still up.
 */
@Composable
private fun rememberNoCoverPainter(colors: ColorScheme, @DrawableRes icon: Int): Painter {
    val note = painterResource(icon)
    return remember(note, colors) {
        val middle = colors.primaryContainer
        val edge = Color(
            red = middle.red * NoCoverGlowEdgeBrightness,
            green = middle.green * NoCoverGlowEdgeBrightness,
            blue = middle.blue * NoCoverGlowEdgeBrightness,
        )
        val glow = Brush.radialGradient(0f to middle, 0.7f to edge, 1f to Color.Black)
        NoCoverPainter(note, glow, colors.primary.copy(alpha = 0.22f), noteSize = 104.dp)
    }
}

/**
 * The song's progress runs along the edge, clockwise from just right of the clock (0° is 3
 * o'clock) to just left of it, this far from the middle and this wide.
 */
const val RingStart = -70f
const val RingSweep = 320f
val RingRadius = 112.dp
val RingWidth = 4.dp

/** The ring's faint track, under the part played. */
val RingTrack = Color.White.copy(alpha = 0.16f)

/** How much of the primary color tints a see-through button over the cover. */
private const val SeeThroughTint = 0.28f

/**
 * A see-through button's colors, like Play's: the cover shows through a tint of the primary color,
 * under a white icon.
 */
@Composable
fun seeThroughButtonColors(): IconButtonColors = IconButtonDefaults.filledIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = SeeThroughTint),
    contentColor = Color.White,
)

/** The same for a button with a label, e.g. a song's actions: the label and its icon white. */
@Composable
fun seeThroughLabelButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = SeeThroughTint),
    contentColor = Color.White,
    iconColor = Color.White,
)

/**
 * Behind numbers or buttons, the cover is dimmed more than on Now playing: on the seek screen, and
 * behind a song's actions.
 */
const val DimmedCoverAlpha = 0.45f

/** The shadow of the icons on see-through buttons. */
val SeeThroughIconShadow = Shadow(radius = 3.dp, offset = DpOffset(0.dp, 1.dp), alpha = 0.65f)

/**
 * A small time that's a button, the elapsed time on Now playing and the length on the seek screen,
 * takes taps this far around its text: the smallest touch target, and wider.
 */
val TimeTapHeight = 48.dp
val TimeTapWidth = 80.dp

/** An icon of [size] in the content color, over a soft [shadow] of its own shape. */
@Composable
fun ShadowedIcon(@DrawableRes id: Int, contentDescription: String, shadow: Shadow, size: Dp = 28.dp) {
    val icon = ImageVector.vectorResource(id)
    val outline = remember(icon) { IconOutline(icon) }
    Icon(icon, contentDescription, Modifier.size(size).dropShadow(outline, shadow))
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

/** For previews: a 16:9 stand-in cover, in the Yamato cover's red, fading to near black, and its colors. */
@Composable
fun rememberStandInArt(): CoverArt = remember {
    val picture = createBitmap(320, 180).applyCanvas {
        val gradient = LinearGradient(0f, 0f, 320f, 180f, 0xFF93291C.toInt(), 0xFF101010.toInt(), Shader.TileMode.CLAMP)
        drawPaint(Paint().apply { shader = gradient })
    }
    CoverArt(SongCover(Uri.EMPTY, 0), picture.asImageBitmap(), seedColorScheme(0xFF93291C.toInt()))
}
