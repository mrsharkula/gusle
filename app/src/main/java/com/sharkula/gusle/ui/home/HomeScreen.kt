package com.sharkula.gusle.ui.home

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter
import com.sharkula.gusle.ui.songs.SongRow
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.seedColorScheme

private data class Source(@DrawableRes val icon: Int, @StringRes val label: Int)

// Shown on Home but not available until after v1.
private val laterSources = listOf(
    Source(R.drawable.ic_queue_music, R.string.playlists),
    Source(R.drawable.ic_album, R.string.albums),
    Source(R.drawable.ic_person, R.string.artists),
    Source(R.drawable.ic_folder, R.string.folders),
)

@Composable
fun HomeScreen(
    songCount: Int,
    /** The player's song, playing or paused, shown on top; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    /** The song on top was tapped. */
    onPlaying: () -> Unit,
    onAllSongs: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val laterColors = ButtonDefaults.filledTonalButtonColors()

    ListScreen(
        title = stringResource(R.string.home_title),
        edgeButton = {
            EdgeButton(
                onClick = onSettings,
                buttonSize = EdgeButtonSize.Small,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(stringResource(R.string.settings))
            }
        },
    ) { transformationSpec ->
        if (playing != null) {
            item(key = "playing") {
                SongRow(
                    playing,
                    // Left out when the song has no artist tag.
                    secondaryLabel = playing.artist,
                    isPlaying = isPlaying,
                    noCover = rememberRowNoCoverPainter(),
                    transformationSpec = transformationSpec,
                    onClick = onPlaying,
                )
            }
        }
        // The highlighted row, unless the playing song is on top: then it's a row like the rest.
        item(key = "all songs") {
            ListButton(
                onClick = onAllSongs,
                transformationSpec = transformationSpec,
                colors = if (playing == null) {
                    ButtonDefaults.filledVariantButtonColors()
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                icon = {
                    IconCircle(
                        icon = R.drawable.ic_music_note,
                        background = if (playing == null) colors.primary else colors.secondaryContainer,
                        tint = if (playing == null) colors.onPrimary else colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.all_songs)) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.song_count, songCount, songCount)) },
            )
        }
        items(laterSources, key = { it.label }) { source ->
            ListButton(
                onClick = {},
                transformationSpec = transformationSpec,
                enabled = false,
                colors = laterColors,
                icon = {
                    // Disabled: the circle uses the button's own disabled container color;
                    // the icon keeps its disabled content color.
                    IconCircle(
                        icon = source.icon,
                        background = laterColors.disabledContainerColor,
                    )
                },
                label = { Text(stringResource(source.label)) },
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    GusleTheme {
        HomeScreen(songCount = 51, playing = null, isPlaying = false, onPlaying = {}, onAllSongs = {}, onSettings = {})
    }
}

private val previewSong = Song(1, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0)

/** In the app theme's colors, as for a song without a cover. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPlayingPreview() {
    GusleTheme {
        HomeScreen(songCount = 51, playing = previewSong, isPlaying = true, onPlaying = {}, onAllSongs = {}, onSettings = {})
    }
}

/** In the Yamato cover's colors. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPlayingCoverPreview() {
    GusleTheme(colorScheme = seedColorScheme(0xFF93291C.toInt())) {
        HomeScreen(songCount = 51, playing = previewSong, isPlaying = true, onPlaying = {}, onAllSongs = {}, onSettings = {})
    }
}
