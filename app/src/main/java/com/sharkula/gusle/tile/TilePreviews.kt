package com.sharkula.gusle.tile

import android.content.Context
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.ui.theme.AppTheme
import com.sharkula.gusle.ui.theme.seedColorScheme
import java.time.Instant

// The tile in every state, for Android Studio's tile previews. Previews can't read covers, so the
// songs have no picture behind them; their colors are Yamato's, as in the mockups.

/** Yamato's cover's colors (seed #93291C), as on Home and Now playing. */
private val yamatoColors by lazy { seedColorScheme(0xFF93291C.toInt()) }

private fun song(isPlaying: Boolean) = TileShows.Song(
    songId = 1,
    title = "Yamato",
    artist = "Sabaton",
    isPlaying = isPlaying,
    positionMs = 83_000,
    durationMs = 225_000,
    at = Instant.now(),
    hasCover = false,
)

private fun preview(context: Context, shows: TileShows, colors: ColorScheme) =
    TilePreviewData(onTileResourceRequest = { TileResources.icons().build() }) { request ->
        Tile.Builder()
            .setResourcesVersion(TileResources.icons().build().version)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    tileLayout(context, request.deviceConfiguration, shows, colors, TileLaunches(clickable(), clickable())),
                ),
            )
            .build()
    }

@Preview(device = WearDevices.LARGE_ROUND, name = "Playing")
fun playingTilePreview(context: Context) = preview(context, song(isPlaying = true), yamatoColors)

@Preview(device = WearDevices.LARGE_ROUND, name = "Paused")
fun pausedTilePreview(context: Context) = preview(context, song(isPlaying = false), yamatoColors)

@Preview(device = WearDevices.LARGE_ROUND, name = "A favorite")
fun favoriteTilePreview(context: Context) = preview(context, song(isPlaying = true).copy(isFavorite = true), yamatoColors)

@Preview(device = WearDevices.LARGE_ROUND, name = "No cover: the app theme")
fun noCoverTilePreview(context: Context) =
    preview(context, song(isPlaying = true).copy(title = "Ghost Division"), AppTheme.Walnut.colors)

@Preview(device = WearDevices.LARGE_ROUND, name = "Nothing to play")
fun nothingTilePreview(context: Context) =
    preview(context, TileShows.Nothing(canShuffle = true), AppTheme.Walnut.colors)

@Preview(device = WearDevices.LARGE_ROUND, name = "Nothing to play, no songs")
fun noSongsTilePreview(context: Context) =
    preview(context, TileShows.Nothing(canShuffle = false), AppTheme.Walnut.colors)

@Preview(device = WearDevices.SMALL_ROUND, name = "Playing, small watch")
fun smallPlayingTilePreview(context: Context) = preview(context, song(isPlaying = true), yamatoColors)
