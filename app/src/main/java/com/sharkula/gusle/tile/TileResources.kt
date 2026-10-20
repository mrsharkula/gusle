package com.sharkula.gusle.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.protolayout.ResourceBuilders.ImageResource
import androidx.wear.protolayout.ResourceBuilders.InlineImageResource
import androidx.wear.protolayout.ResourceBuilders.Resources
import com.sharkula.gusle.R
import com.sharkula.gusle.coverColors
import com.sharkula.gusle.ui.covers.SongCover
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The cover behind the tile is dimmed to this opacity over black, as in the design. */
private const val CoverAlpha = 0.5f

/** The cover fades out from this fraction of its radius to nothing at the edge, as on Now playing. */
private const val FadeFrom = 0.35f

/** The cover's JPEG quality: it's dimmed, so a little loss doesn't show, and it stays ~20 KB. */
private const val CoverQuality = 85

/**
 * Part of every resources version, to bump when the icons change: the watch keeps a tile's
 * resources by their version, also across updates of Gusle.
 */
private const val IconsVersion = "2"

/** Ends the version of a cover asked for again ([TileResources.version]). */
private const val AgainSuffix = "again"

/** The icons the tile draws, by their IDs in its layout. */
enum class TileIcon(@param:DrawableRes val drawable: Int) {
    Play(R.drawable.ic_play_arrow),
    Pause(R.drawable.ic_pause),
    Previous(R.drawable.ic_skip_previous),
    Back(R.drawable.ic_replay_10),
    Forward(R.drawable.ic_forward_10),
    Next(R.drawable.ic_skip_next),
    Favorite(R.drawable.ic_favorite),
    NotFavorite(R.drawable.ic_favorite_outline),
    Note(R.drawable.ic_music_note);

    val id: String get() = "icon_" + name.lowercase()
}

/**
 * The pictures the tile's layout names: its icons, and the cover behind a song. The watch asks for
 * them only when their version is new to it, so the version names the cover, and a song's cover is
 * read for the tile once, not every time the tile changes. It keeps only the latest version's, so
 * a song the tile comes back to is asked for again.
 */
object TileResources {
    /** The cover's ID in the layout. */
    const val CoverId = "cover"

    /**
     * The version for the icons and [cover] at [sizePx] across; with no cover, just the icons.
     * [again] gives the cover another version, for the watch to ask for it again: one it has
     * without its picture, as that couldn't be read.
     */
    fun version(cover: SongCover?, sizePx: Int, again: Boolean = false): String =
        if (cover == null) {
            IconsVersion
        } else {
            "$IconsVersion|${cover.songUri}|${cover.fileModified}|$sizePx" + if (again) "|$AgainSuffix" else ""
        }

    /**
     * The resources of [version]: the icons, and the cover it names, if any. A cover that can't be
     * read now goes as a black dot all the same, and [onCoverUnread] hears of it: answering with no
     * resources would leave the tile black.
     */
    suspend fun make(context: Context, version: String, onCoverUnread: (SongCover) -> Unit): Resources {
        val resources = icons(version)
        coverOf(version)?.let { (cover, sizePx) ->
            val picture = coverPicture(context, cover, sizePx) ?: blackDot().also { onCoverUnread(cover) }
            resources.addIdToImageMapping(
                CoverId,
                ImageResource.Builder()
                    .setInlineResource(
                        InlineImageResource.Builder()
                            .setData(picture.jpeg)
                            // Compressed: the watch reads the format from the data. The size is
                            // given too, as older renderers scale by it.
                            .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                            .setWidthPx(picture.sizePx)
                            .setHeightPx(picture.sizePx)
                            .build(),
                    )
                    .build(),
            )
        }
        return resources.build()
    }

    /** The icons alone, as [version]: all the resources a tile without a cover needs, e.g. a preview's. */
    fun icons(version: String = IconsVersion): Resources.Builder {
        val resources = Resources.Builder().setVersion(version)
        for (icon in TileIcon.entries) {
            resources.addIdToImageMapping(
                icon.id,
                ImageResource.Builder()
                    .setAndroidResourceByResId(AndroidImageResourceByResId.Builder().setResourceId(icon.drawable).build())
                    .build(),
            )
        }
        return resources
    }

    /** The cover and its size that [version] names, or null when it names none. */
    private fun coverOf(version: String): Pair<SongCover, Int>? {
        val parts = version.split('|')
        val again = parts.size == 5 && parts[4] == AgainSuffix
        if (parts.size != 4 && !again) return null
        val modified = parts[2].toLongOrNull() ?: return null
        val sizePx = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return SongCover(parts[1].toUri(), modified) to sizePx
    }
}

/** A square picture as a JPEG, [sizePx] across. */
private class SquareJpeg(val jpeg: ByteArray, val sizePx: Int)

/**
 * [cover] as the tile shows it, [sizePx] across: center-cropped, dimmed and fading into black. A
 * song without a cover gets a [blackDot]. Null when the cover can't be read now, which may pass.
 */
private suspend fun coverPicture(context: Context, cover: SongCover, sizePx: Int): SquareJpeg? {
    // The same size as Now playing's, so one copy in Coil's memory serves both.
    val read = context.coverColors.forTile(cover, sizePx)
    if (read.colors == null) return null
    val picture = read.picture ?: return blackDot()
    return withContext(Dispatchers.Default) { dimmedAndFaded(picture, sizePx).toJpeg() }
}

/** A black dot in place of a cover, which doesn't show. */
private fun blackDot(): SquareJpeg = createBitmap(1, 1).apply { eraseColor(android.graphics.Color.BLACK) }.toJpeg()

/** This square picture as a JPEG. */
private fun Bitmap.toJpeg(): SquareJpeg {
    val jpeg = ByteArrayOutputStream().use { bytes ->
        compress(Bitmap.CompressFormat.JPEG, CoverQuality, bytes)
        bytes.toByteArray()
    }
    return SquareJpeg(jpeg, width)
}

/** [picture] center-cropped to a [sizePx] square, at [CoverAlpha] over black, fading out toward its round edge. */
private fun dimmedAndFaded(picture: Bitmap, sizePx: Int): Bitmap {
    val size = sizePx.toFloat()
    val out = createBitmap(sizePx, sizePx)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val layer = canvas.saveLayer(0f, 0f, size, size, null)
    val scale = max(size / picture.width, size / picture.height)
    val width = picture.width * scale
    val height = picture.height * scale
    canvas.drawBitmap(
        picture,
        null,
        RectF((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2),
        Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * CoverAlpha).roundToInt() },
    )
    // Keeps the picture where the gradient is opaque: all of it inside FadeFrom, less and less
    // outside, none at the edge.
    val radius = size / 2
    val fade = Paint().apply {
        shader = RadialGradient(
            radius,
            radius,
            radius,
            intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, FadeFrom, 1f),
            Shader.TileMode.CLAMP,
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    canvas.drawRect(0f, 0f, size, size, fade)
    canvas.restoreToCount(layer)
    return out
}
