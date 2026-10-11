package com.sharkula.gusle.ui.covers

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import androidx.wear.compose.material3.ColorScheme
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.sharkula.gusle.coverColors
import com.sharkula.gusle.ui.theme.seedColorScheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How many covers' colors are kept. A scheme is a few dozen colors, so this is tiny. */
private const val KeptSchemes = 40

/**
 * A cover's main color is looked for in a copy at most this many pixels across. Material looks
 * at wallpapers at 112 px, but on the watch a photo's colors then take ~0.65 s to sort out, and at
 * 64 px ~0.2 s, for the same color.
 */
private const val ColorSampleSize = 64

/** A cover's pixels are grouped into at most this many colors before its main one is picked. */
private const val CoverColorGroups = 128

/**
 * A cover that couldn't be read, e.g. while its file is still being written, is tried again after
 * these waits.
 */
private val RetryWaitsMs = longArrayOf(2_000, 8_000)

/**
 * A song's cover as a screen shows it, and its colors. [picture] is null for a song without a
 * cover, or whose cover couldn't be read. [colors] is null when the song has none of its own: it's
 * shown in the app theme's ([com.sharkula.gusle.ui.theme.LocalAppTheme]).
 */
class CoverArt(val cover: SongCover?, val picture: ImageBitmap?, val colors: ColorScheme?)

/**
 * A cover's own colors, once worked out: null [scheme] for a song without a cover, or with one
 * that has no real color in it, which is shown in the app theme's colors. The
 * theme's colors aren't kept here, so a change of theme reaches every such song at once.
 */
class OwnColors(val scheme: ColorScheme?)

/** The [OwnColors] of every song that has none. */
private val NoOwnColors = OwnColors(null)

/**
 * The colors for each song's cover: a Material 3 color scheme generated from the cover's main
 * color, or none, for a song without a cover, or with one that has no real color in it. The last
 * few dozen are kept, for every screen that shows the playing song's colors.
 */
class CoverColors(private val context: Context) {
    private val schemes = LruCache<SongCover, OwnColors>(KeptSchemes)
    private val scope = MainScope()

    /**
     * The colors [show] is working out, so screens asking at the same time share the work. Only
     * used on the main thread.
     */
    private val working = HashMap<SongCover, Deferred<OwnColors?>>()

    /** The playing song's cover, as [show] last heard of it. */
    private var playingCover: SongCover? = null

    /**
     * The playing song's colors, for the screens that show them without its cover (see [show]):
     * its cover's once they're known, and until then those of the song before. Null while they're
     * the app theme's: at first, and for a song without colors of its own. A screen that reads them
     * is composed again when they change. Only used on the main thread.
     */
    var playingColors: ColorScheme? by mutableStateOf(null)
        private set

    /**
     * The art last loaded with [art], so a screen opened again on the same song shows it in its
     * first frame. Only used on the main thread.
     */
    var lastArt: CoverArt? = null
        private set

    /** [cover]'s colors if they're known already, so a screen can show them in its first frame. */
    fun known(cover: SongCover): OwnColors? = schemes[cover]

    /**
     * Makes [playingColors] those of the playing song's [cover], once they're known. Screens asking
     * at the same time share the work, which goes on when they leave, so the colors are kept for
     * the next one. A cover that couldn't be read is tried again a little later, in the app theme's
     * colors meanwhile.
     */
    suspend fun show(cover: SongCover?) {
        playingCover = cover
        var tries = 0
        while (true) {
            val colors = if (cover == null) NoOwnColors else known(cover) ?: workOut(cover)
            if (cover == playingCover) playingColors = colors?.scheme
            if (colors != null || tries == RetryWaitsMs.size) return
            delay(RetryWaitsMs[tries++])
        }
    }

    /** [cover]'s colors, worked out once for everyone asking meanwhile; null when it couldn't be read. */
    private suspend fun workOut(cover: SongCover): OwnColors? {
        val work = working.getOrPut(cover) { scope.async { readColors(cover) } }
        work.invokeOnCompletion { working.remove(cover, work) }
        return work.await()
    }

    /** Reads a small copy of [cover] and works out its colors; null when it couldn't be read. */
    private suspend fun readColors(cover: SongCover): OwnColors? =
        // Only the colors are kept: a small copy in Coil's cache would push full-size covers out.
        when (val result = read(cover, ColorSampleSize, Scale.FIT, keep = false)) {
            is SuccessResult -> colorsOf(cover, result.image.toBitmap())
            is ErrorResult -> noColors(cover, result)
        }

    /**
     * [cover], at least [sizePx] across, with its colors worked out from the same picture: the
     * file is read once, which on the watch takes up to a second.
     */
    suspend fun art(cover: SongCover?, sizePx: Int): CoverArt {
        val art = if (cover == null) {
            CoverArt(null, null, null)
        } else {
            when (val result = read(cover, sizePx, Scale.FILL, keep = true)) {
                is SuccessResult -> {
                    val picture = result.image.toBitmap()
                    // Onto the GPU now, while its colors are worked out, rather than in the frame it
                    // goes up in: ~2.3 ms of that frame on the watch.
                    picture.prepareToDraw()
                    CoverArt(cover, picture.asImageBitmap(), (known(cover) ?: colorsOf(cover, picture)).scheme)
                }
                is ErrorResult -> CoverArt(cover, null, noColors(cover, result)?.scheme)
            }
        }
        lastArt = art
        return art
    }

    /** The cover in memory rather than on the GPU, so its pixels can be read. */
    private suspend fun read(cover: SongCover, sizePx: Int, scale: Scale, keep: Boolean): ImageResult {
        val cachePolicy = if (keep) CachePolicy.ENABLED else CachePolicy.DISABLED
        val request = ImageRequest.Builder(context)
            .data(cover)
            .size(sizePx)
            .scale(scale)
            .allowHardware(false)
            .memoryCachePolicy(cachePolicy)
            .build()
        return context.imageLoader.execute(request)
    }

    /** Works out [cover]'s colors from its [picture], off the main thread, and keeps them. */
    private suspend fun colorsOf(cover: SongCover, picture: Bitmap): OwnColors {
        val scheme = withContext(Dispatchers.Default) {
            mainColor(pixelsOf(sample(picture)))?.let(::seedColorScheme)
        }
        // Another screen may have worked them out meanwhile. Every screen gets the same ones, so
        // none is composed again for colors that look the same.
        schemes[cover]?.let { return it }
        val colors = if (scheme == null) NoOwnColors else OwnColors(scheme)
        schemes.put(cover, colors)
        return colors
    }

    /**
     * The colors for a cover that couldn't be read: none of its own, kept, when the song has no
     * cover. Any other failure may pass, so it's null and the next time tries again.
     */
    private fun noColors(cover: SongCover, result: ErrorResult): OwnColors? {
        if (result.throwable !is NoCoverException) return null
        schemes.put(cover, NoOwnColors)
        return NoOwnColors
    }
}

/**
 * The colors for the playing song's [cover], for a screen that shows them without the cover
 * itself: right away when they're known, otherwise those shown for the song before stay until
 * they're ready. Null for the app theme's ([com.sharkula.gusle.ui.theme.GusleTheme] fills them
 * in): at first, and for a song without colors of its own. They jump rather than fade, since fading
 * every role would recompose the whole screen each frame.
 */
@Composable
fun playingSongColors(cover: SongCover?): ColorScheme? {
    val coverColors = LocalContext.current.coverColors
    LaunchedEffect(cover) { coverColors.show(cover) }
    if (cover == null) return null
    val known = coverColors.known(cover) ?: return coverColors.playingColors
    return known.scheme
}

/** [picture], or a copy of it at most [ColorSampleSize] across. */
private fun sample(picture: Bitmap): Bitmap {
    val factor = ColorSampleSize.toFloat() / maxOf(picture.width, picture.height)
    if (factor >= 1f) return picture
    val width = (picture.width * factor).roundToInt().coerceAtLeast(1)
    val height = (picture.height * factor).roundToInt().coerceAtLeast(1)
    return picture.scale(width, height)
}

private fun pixelsOf(bitmap: Bitmap): IntArray =
    IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

/**
 * The color a cover is mostly about, picked the way Material picks a theme's color from a
 * wallpaper: plenty of it, and colorful. Null when there's none, e.g. in black and white, rather
 * than the library's stand-in blue.
 */
fun mainColor(pixels: IntArray): Int? =
    Score.score(QuantizerCelebi.quantize(pixels, CoverColorGroups), desired = 1, fallbackColorArgb = null)
        .firstOrNull()
