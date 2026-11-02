package com.sharkula.gusle.tile

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ColorBuilders.argb as colorProp
import androidx.wear.protolayout.DimensionBuilders.DpProp
import androidx.wear.protolayout.DimensionBuilders.HorizontalLayoutConstraint
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.ColorFilter
import androidx.wear.protolayout.LayoutElementBuilders.FontSetting
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32.IntFormatter
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.PrimaryLayoutMargins
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.background
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.clip
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.argb
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.formatLength
import com.sharkula.gusle.ui.nowplaying.JumpStep
import java.time.Instant
import androidx.wear.protolayout.material3.ColorScheme as TileColorScheme

/** The tile is drawn for a screen this wide (dp), like Gusle's screens; it's scaled to the watch's. */
private const val DesignWidth = 233f

/** The cover behind is this wide (dp of [DesignWidth]), as on Now playing. */
private const val CoverDiameter = 161f

/** The bar under the buttons is this wide (dp of [DesignWidth]) and this thick (dp). */
private const val BarWidth = 101f
private const val BarHeight = 3f

/** The buttons' see-through fill: the primary color at this opacity, over the cover. */
private const val ButtonFillAlpha = 0.32f

/** The bar's track: the text color at this opacity. */
private const val TrackAlpha = 0.2f

/** The round buttons over the cover are this tall (dp): five in a row only fit at less than 48. */
private const val ButtonSize = 40f

/** The buttons' icons are this large (dp), the edge button's this large: Material's sizes. */
private const val ButtonIconSize = 24f
private const val EdgeButtonIconSize = 24f

/** What the tile shows. */
sealed interface TileShows {
    /**
     * A song: the one the player has, or had last. [positionMs] is where it was at [at], and goes on
     * from there while it [isPlaying]. [hasCover] when the tile's resources ([TileResources]) have its
     * cover; not in a preview.
     */
    data class Song(
        /** The song's [com.sharkula.gusle.data.Song.id], which its heart's tap names ([FavoriteTap]). */
        val songId: Long,
        val title: String,
        val artist: String?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val at: Instant,
        val hasCover: Boolean,
        val isFavorite: Boolean = false,
    ) : TileShows {
        /** Its length, in whole seconds, as the tile shows it. */
        val durationS: Int get() = (durationMs / 1000).toInt().coerceAtLeast(0)

        /** Where it is, in whole seconds, as the tile shows it while it stands still. */
        val positionS: Int get() = (positionMs / 1000).toInt().coerceIn(0, durationS)

        /** Whether its time counts on by itself, by the watch's clock: while it plays, if its length is known. */
        val counts: Boolean get() = isPlaying && durationS > 0

        /** When it would have started, had it played straight through: its time counts from there. */
        val startedAt: Instant get() = at.minusMillis(positionMs)
    }

    /** Nothing to play: no song saved. [canShuffle] when there are songs to shuffle. */
    data class Nothing(val canShuffle: Boolean) : TileShows
}

/**
 * What the taps that open Gusle do: on Now playing (the song), or as its launcher icon does (the
 * "Gusle" title, and the message when there's nothing to play). They're made where the tile is
 * ([NowPlayingTileService]).
 */
class TileLaunches(val nowPlaying: Clickable, val home: Clickable)

/** The IDs of the tile's buttons, which come back in the tile request that a tap makes. */
enum class TileButton(val id: String) {
    Play("play"),
    Pause("pause"),
    Previous("previous"),
    Next("next"),
    ShuffleAll("shuffle_all");

    companion object {
        fun of(id: String?): TileButton? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A tap on the tile's heart, as its ID comes back in the tile request: it makes the song the tile
 * showed ([songId]) a favorite, or not, as the heart then offered ([favorite]), whatever has changed
 * since, e.g. the next song playing, or the heart tapped on Now playing.
 */
data class FavoriteTap(val songId: Long, val favorite: Boolean) {
    val id: String get() = "$FavoritePrefix$songId:${if (favorite) 1 else 0}"

    companion object {
        /** The tap with this [id], or null when it isn't the heart's. */
        fun of(id: String?): FavoriteTap? {
            if (id == null || !id.startsWith(FavoritePrefix)) return null
            val parts = id.removePrefix(FavoritePrefix).split(':')
            val songId = parts.getOrNull(0)?.toLongOrNull() ?: return null
            return when (parts.getOrNull(1)) {
                "1" -> FavoriteTap(songId, favorite = true)
                "0" -> FavoriteTap(songId, favorite = false)
                else -> null
            }
        }
    }
}

private const val FavoritePrefix = "favorite:"

/**
 * A tap on one of the tile's jump buttons, as its ID comes back in the tile request: back or
 * [forward], by the [step] the tile showed, whatever has been chosen since: a tile made before the
 * choice, and not made anew yet, jumps as its icons say.
 */
data class JumpTap(val forward: Boolean, val step: JumpStep) {
    val id: String get() = "$JumpPrefix${if (forward) "forward" else "back"}:${step.id}"

    /** How far it jumps: on, or back if it's negative. */
    val byMs: Long get() = if (forward) step.ms else -step.ms

    companion object {
        /** The tap with this [id], or null when it isn't a jump button's. */
        fun of(id: String?): JumpTap? {
            if (id == null || !id.startsWith(JumpPrefix)) return null
            val parts = id.removePrefix(JumpPrefix).split(':')
            val forward = when (parts.getOrNull(0)) {
                "forward" -> true
                "back" -> false
                else -> return null
            }
            val step = JumpStep.entries.firstOrNull { it.id == parts.getOrNull(1) } ?: return null
            return if (parts.size == 2) JumpTap(forward, step) else null
        }
    }
}

private const val JumpPrefix = "jump:"

/**
 * The tile's layout: [shows] in [colors] (a song's cover's, or the app theme's), on a black
 * background, as chosen in designs/notes/TILE.md (3d): the cover dimmed behind, the title and artist,
 * previous, back, the favorite heart, forward and next (designs/notes/FAVORITES.md; back and forward
 * jump by [jumpStep], chosen in Settings), the time and a bar under them, and play/pause in the
 * edge button. Material 3 tile parts throughout, with Gusle's own colors rather than the watch's.
 */
fun tileLayout(
    context: Context,
    device: DeviceParameters,
    shows: TileShows,
    colors: ColorScheme,
    launches: TileLaunches,
    jumpStep: JumpStep,
): LayoutElement =
    materialScope(context, device, allowDynamicTheme = false, defaultColorScheme = colors.toTileColors()) {
        when (shows) {
            is TileShows.Song -> songLayout(context, shows, colors, launches, jumpStep)
            is TileShows.Nothing -> nothingLayout(context, shows, launches.home)
        }
    }

// Images by resource ID, which Gusle gives the watch in onTileResourcesRequest only when their
// version is new to it (see TileResources). The newer way (the request's ProtoLayoutScope) needs the
// pictures for every tile request, the cover too, to version them; this one reads the cover only for
// a new song. Deprecated in protolayout 1.4, still the documented way for older watches.
@Suppress("DEPRECATION")
private fun MaterialScope.songLayout(
    context: Context,
    song: TileShows.Song,
    colors: ColorScheme,
    launches: TileLaunches,
    jumpStep: JumpStep,
): LayoutElement {
    val scale = deviceConfiguration.screenWidthDp / DesignWidth
    val layout = primaryLayout(
        titleSlot = { appTitle(context, launches.home) },
        mainSlot = {
            column(
                songText(song, launches.nowPlaying),
                spacer(height = dp(4f)),
                // Five in a row only fit smaller than the usual 48 dp, and with the least margin.
                buttonGroup(height = dp(ButtonSize)) {
                    buttonGroupItem {
                        songButton(TileButton.Previous.id, TileIcon.Previous, context.getString(R.string.previous_song))
                    }
                    buttonGroupItem {
                        songButton(JumpTap(forward = false, jumpStep).id, TileIcon.back(jumpStep), jumpStep.backLabel(context.resources))
                    }
                    buttonGroupItem {
                        songButton(
                            FavoriteTap(song.songId, favorite = !song.isFavorite).id,
                            if (song.isFavorite) TileIcon.Favorite else TileIcon.NotFavorite,
                            context.getString(if (song.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        )
                    }
                    buttonGroupItem {
                        songButton(JumpTap(forward = true, jumpStep).id, TileIcon.forward(jumpStep), jumpStep.forwardLabel(context.resources))
                    }
                    buttonGroupItem { songButton(TileButton.Next.id, TileIcon.Next, context.getString(R.string.next_song)) }
                },
                spacer(height = dp(6f)),
                progress(song, colors, BarWidth * scale),
                width = expand(),
            )
        },
        margins = PrimaryLayoutMargins.MIN_PRIMARY_LAYOUT_MARGIN,
        bottomSlot = {
            val button = if (song.isPlaying) TileButton.Pause else TileButton.Play
            iconEdgeButton(
                onClick = clickable(loadAction(), button.id),
                modifier = LayoutModifier.contentDescription(
                    context.getString(if (song.isPlaying) R.string.pause else R.string.play),
                ),
            ) {
                tintedIcon(
                    (if (song.isPlaying) TileIcon.Pause else TileIcon.Play).id,
                    EdgeButtonIconSize,
                    colorScheme.onPrimary,
                )
            }
        },
    )
    if (!song.hasCover) return layout
    // The cover behind everything, in the middle, as large as Now playing's. It's dimmed and fades
    // into black already ([TileResources]): tiles draw pictures as they are.
    val coverSize = dp(CoverDiameter * scale)
    return box(
        Image.Builder()
            .setResourceId(TileResources.CoverId)
            .setWidth(coverSize)
            .setHeight(coverSize)
            .build(),
        layout,
        width = expand(),
        height = expand(),
    )
}

/** "Gusle" at the top; a tap opens Gusle, as its launcher icon does. */
private fun MaterialScope.appTitle(context: Context, openGusle: Clickable): LayoutElement =
    text(context.getString(R.string.app_name).layoutString, modifier = LayoutModifier.clickable(openGusle))

/** The title and the artist, one line each; a tap opens Now playing. */
private fun MaterialScope.songText(song: TileShows.Song, openNowPlaying: Clickable): LayoutElement {
    val texts = listOfNotNull(
        text(
            song.title.layoutString,
            typography = Typography.TITLE_MEDIUM,
            color = colorScheme.onSurface,
            maxLines = 1,
        ),
        song.artist?.let { artist ->
            text(
                artist.layoutString,
                typography = Typography.BODY_SMALL,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
    )
    return column(
        *texts.toTypedArray(),
        width = expand(),
        modifier = LayoutModifier
            .clickable(openNowPlaying)
            .contentDescription(listOfNotNull(song.title, song.artist).joinToString(", "))
            // Kept off the round screen's edges, where long titles would be cut.
            .padding(start = 10f, top = 0f, end = 10f, bottom = 0f, rtlAware = true),
    )
}

/**
 * One of the round, see-through buttons over the cover, which [clickId] names to the tile request
 * ([TileButton.id], [JumpTap.id] or [FavoriteTap.id]); [label] is for screen readers.
 */
private fun MaterialScope.songButton(clickId: String, icon: TileIcon, label: String): LayoutElement {
    val primary = colorScheme.primary.staticArgb
    return iconButton(
        onClick = clickable(loadAction(), clickId),
        modifier = LayoutModifier.contentDescription(label),
        width = expand(),
        height = expand(),
        colors = ButtonColors(
            containerColor = Color(primary).copy(alpha = ButtonFillAlpha).toArgb().argb,
            iconColor = Color.White.toArgb().argb,
        ),
        iconContent = { tintedIcon(icon.id, ButtonIconSize, Color.White.toArgb().argb) },
    )
}

/**
 * Where the song is: the time it's at, a bar [barWidth] dp wide, and its length. While it plays, the
 * time and the bar go on by the watch's own clock, from where it was at [TileShows.Song.at], with
 * nothing asked of Gusle; a paused song's stand still.
 */
private fun MaterialScope.progress(song: TileShows.Song, colors: ColorScheme, barWidth: Float): LayoutElement {
    val durationS = song.durationS
    val positionS = song.positionS
    val fraction = if (durationS > 0) positionS.toFloat() / durationS else 0f
    val elapsed: LayoutString
    val played = if (song.counts) {
        // From when the song would have started, had it played straight through, to now; never
        // past its end, which the next song's tile comes soon after.
        val sinceStart = DynamicInstant.withSecondsPrecision(song.startedAt)
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
            .toIntSeconds()
        val seconds = DynamicInt32.onCondition(sinceStart.gt(durationS)).use(durationS)
            .elseUse(DynamicInt32.onCondition(sinceStart.lt(0)).use(0).elseUse(sinceStart))
        elapsed = LayoutString(
            formatLength(positionS * 1000L),
            dynamicLength(seconds, longest = durationS),
            StringLayoutConstraint.Builder(formatLength(durationS * 1000L).replace(Regex("[0-9]"), "0"))
                .setAlignment(LayoutElementBuilders.TEXT_ALIGN_END)
                .build(),
        )
        DpProp.Builder(fraction * barWidth)
            .setDynamicValue(seconds.asFloat().div(durationS.toFloat()).times(barWidth))
            .build()
    } else {
        elapsed = formatLength(positionS * 1000L).layoutString
        dp(fraction * barWidth)
    }
    val numbers = listOf(FontSetting.tabularNum())
    val track = colors.onSurface.copy(alpha = TrackAlpha).toArgb().argb
    return row(
        text(elapsed, typography = Typography.LABEL_SMALL, color = colorScheme.onSurface, settings = numbers),
        spacer(width = dp(6f)),
        box(
            spacer(
                width = played,
                height = dp(BarHeight),
                modifier = LayoutModifier.background(colorScheme.primary).clip(BarHeight / 2),
                horizontalLayoutConstraint = HorizontalLayoutConstraint.Builder(barWidth)
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .build(),
            ),
            width = dp(barWidth),
            height = dp(BarHeight),
            modifier = LayoutModifier.background(track).clip(BarHeight / 2),
            horizontalAlignment = LayoutElementBuilders.HORIZONTAL_ALIGN_START,
        ),
        spacer(width = dp(6f)),
        text(
            formatLength(durationS * 1000L).layoutString,
            typography = Typography.LABEL_SMALL,
            color = colorScheme.onSurfaceVariant,
            settings = numbers,
        ),
        width = wrap(),
        height = wrap(),
        verticalAlignment = LayoutElementBuilders.VERTICAL_ALIGN_CENTER,
    )
}

/**
 * [seconds] as [formatLength] writes a length, so the time looks the same playing and paused: e.g.
 * 3:31, or 1:02:05 from an hour on. The hours are only looked at in a song that long ([longest]
 * seconds).
 */
private fun dynamicLength(seconds: DynamicInt32, longest: Int): DynamicString {
    val twoDigits = IntFormatter.Builder().setMinIntegerDigits(2).build()
    val colon = DynamicString.constant(":")
    val secondsPart = seconds.rem(60).format(twoDigits)
    val minutes = seconds.div(60).format().concat(colon).concat(secondsPart)
    if (longest < 3600) return minutes
    val hours = seconds.div(3600).format()
        .concat(colon).concat(seconds.div(60).rem(60).format(twoDigits))
        .concat(colon).concat(secondsPart)
    return DynamicString.onCondition(seconds.gte(3600)).use(hours).elseUse(minutes)
}

/**
 * Nothing to play: Gusle's message-screen pattern (an icon in a circle, a title, one line) and the
 * action in the edge button: Shuffle all when there are songs, otherwise Open Gusle. The message
 * opens Gusle on Home.
 */
private fun MaterialScope.nothingLayout(
    context: Context,
    nothing: TileShows.Nothing,
    openGusle: Clickable,
): LayoutElement =
    primaryLayout(
        titleSlot = { appTitle(context, openGusle) },
        mainSlot = {
            val circle = 44f
            column(
                box(
                    tintedIcon(TileIcon.Note.id, 24f, colorScheme.onSecondaryContainer),
                    width = dp(circle),
                    height = dp(circle),
                    modifier = LayoutModifier.background(colorScheme.secondaryContainer).clip(circle / 2),
                ),
                spacer(height = dp(6f)),
                text(
                    context.getString(R.string.nothing_playing).layoutString,
                    typography = Typography.TITLE_MEDIUM,
                    color = colorScheme.onSurface,
                ),
                spacer(height = dp(2f)),
                text(
                    context.getString(
                        if (nothing.canShuffle) R.string.tile_nothing_playing_text else R.string.tile_open_gusle_text,
                    ).layoutString,
                    typography = Typography.BODY_SMALL,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                ),
                width = expand(),
                modifier = LayoutModifier.clickable(openGusle),
            )
        },
        bottomSlot = {
            if (nothing.canShuffle) {
                textEdgeButton(onClick = clickable(loadAction(), TileButton.ShuffleAll.id)) {
                    text(context.getString(R.string.shuffle_all).layoutString)
                }
            } else {
                textEdgeButton(onClick = openGusle) {
                    text(context.getString(R.string.open_gusle).layoutString)
                }
            }
        },
    )

/** The resource [id]'s icon, [size] dp, in [tint]: the image Material's own icon slot holds. */
@Suppress("DEPRECATION") // By resource ID, see songLayout.
private fun tintedIcon(id: String, size: Float, tint: LayoutColor): LayoutElement =
    Image.Builder()
        .setResourceId(id)
        .setWidth(dp(size))
        .setHeight(dp(size))
        .setColorFilter(ColorFilter.Builder().setTint(colorProp(tint.staticArgb)).build())
        .build()

private val String.layoutString: LayoutString get() = LayoutString(this)

private val Color.layoutColor: LayoutColor get() = toArgb().argb

/** These colors as the tile's: every role, on the tile's black background. */
fun ColorScheme.toTileColors(): TileColorScheme =
    TileColorScheme(
        primary = primary.layoutColor,
        primaryDim = primaryDim.layoutColor,
        primaryContainer = primaryContainer.layoutColor,
        onPrimary = onPrimary.layoutColor,
        onPrimaryContainer = onPrimaryContainer.layoutColor,
        secondary = secondary.layoutColor,
        secondaryDim = secondaryDim.layoutColor,
        secondaryContainer = secondaryContainer.layoutColor,
        onSecondary = onSecondary.layoutColor,
        onSecondaryContainer = onSecondaryContainer.layoutColor,
        tertiary = tertiary.layoutColor,
        tertiaryDim = tertiaryDim.layoutColor,
        tertiaryContainer = tertiaryContainer.layoutColor,
        onTertiary = onTertiary.layoutColor,
        onTertiaryContainer = onTertiaryContainer.layoutColor,
        surfaceContainerLow = surfaceContainerLow.layoutColor,
        surfaceContainer = surfaceContainer.layoutColor,
        surfaceContainerHigh = surfaceContainerHigh.layoutColor,
        onSurface = onSurface.layoutColor,
        onSurfaceVariant = onSurfaceVariant.layoutColor,
        outline = outline.layoutColor,
        outlineVariant = outlineVariant.layoutColor,
        background = Color.Black.layoutColor,
        onBackground = onBackground.layoutColor,
        error = error.layoutColor,
        errorDim = errorDim.layoutColor,
        errorContainer = errorContainer.layoutColor,
        onError = onError.layoutColor,
        onErrorContainer = onErrorContainer.layoutColor,
    )

