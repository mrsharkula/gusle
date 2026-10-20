package com.sharkula.gusle.ui.home

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
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

@Composable
fun HomeScreen(
    songCount: Int,
    artistCount: Int,
    albumCount: Int,
    favoriteCount: Int,
    /** The player's song, playing or paused, shown on top; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    /** Whether the song on top is a favorite, which its row shows. */
    playingIsFavorite: Boolean,
    /** The song on top was tapped. */
    onPlaying: () -> Unit,
    onAllSongs: () -> Unit,
    onArtists: () -> Unit,
    onAlbums: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
) {
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
                    isFavorite = playingIsFavorite,
                )
            }
        }
        // The highlighted row, unless the playing song is on top: then it's a row like the rest.
        item(key = "all songs") {
            SourceButton(
                icon = R.drawable.ic_music_note,
                label = stringResource(R.string.all_songs),
                count = pluralStringResource(R.plurals.song_count, songCount, songCount),
                onClick = onAllSongs,
                transformationSpec = transformationSpec,
                highlighted = playing == null,
            )
        }
        item(key = "artists") {
            SourceButton(
                icon = R.drawable.ic_person,
                label = stringResource(R.string.artists),
                count = pluralStringResource(R.plurals.artist_count, artistCount, artistCount),
                onClick = onArtists,
                transformationSpec = transformationSpec,
            )
        }
        item(key = "albums") {
            SourceButton(
                icon = R.drawable.ic_album,
                label = stringResource(R.string.albums),
                count = pluralStringResource(R.plurals.album_count, albumCount, albumCount),
                onClick = onAlbums,
                transformationSpec = transformationSpec,
            )
        }
        // Where Playlists will go, once there are playlists.
        item(key = "favorites") {
            SourceButton(
                icon = R.drawable.ic_favorite,
                label = stringResource(R.string.favorites),
                count = pluralStringResource(R.plurals.song_count, favoriteCount, favoriteCount),
                onClick = onFavorites,
                transformationSpec = transformationSpec,
            )
        }
    }
}

/**
 * A source of songs on Home, e.g. Artists: its [icon] in a circle, its [label], and how many there
 * are ([count]). [highlighted] stands out in the primary color.
 */
@Composable
private fun TransformingLazyColumnItemScope.SourceButton(
    @DrawableRes icon: Int,
    label: String,
    count: String,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    ListButton(
        onClick = onClick,
        transformationSpec = transformationSpec,
        colors = if (highlighted) {
            ButtonDefaults.filledVariantButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = {
            IconCircle(
                icon = icon,
                background = if (highlighted) colors.primary else colors.secondaryContainer,
                tint = if (highlighted) colors.onPrimary else colors.onSecondaryContainer,
            )
        },
        label = { Text(label) },
        secondaryLabel = { Text(count) },
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    GusleTheme {
        HomeScreen(
            songCount = 51,
            artistCount = 6,
            albumCount = 35,
            favoriteCount = 0,
            playing = null,
            isPlaying = false,
            playingIsFavorite = false,
            onPlaying = {},
            onAllSongs = {},
            onArtists = {},
            onAlbums = {},
            onFavorites = {},
            onSettings = {},
        )
    }
}

private val previewSong = Song(1, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0)

/** In the app theme's colors, as for a song without a cover. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPlayingPreview() {
    GusleTheme {
        HomeScreen(
            songCount = 51,
            artistCount = 6,
            albumCount = 35,
            favoriteCount = 4,
            playing = previewSong,
            isPlaying = true,
            playingIsFavorite = true,
            onPlaying = {},
            onAllSongs = {},
            onArtists = {},
            onAlbums = {},
            onFavorites = {},
            onSettings = {},
        )
    }
}

/** In the Yamato cover's colors. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPlayingCoverPreview() {
    GusleTheme(colorScheme = seedColorScheme(0xFF93291C.toInt())) {
        HomeScreen(
            songCount = 51,
            artistCount = 6,
            albumCount = 35,
            favoriteCount = 4,
            playing = previewSong,
            isPlaying = true,
            playingIsFavorite = true,
            onPlaying = {},
            onAllSongs = {},
            onArtists = {},
            onAlbums = {},
            onFavorites = {},
            onSettings = {},
        )
    }
}
