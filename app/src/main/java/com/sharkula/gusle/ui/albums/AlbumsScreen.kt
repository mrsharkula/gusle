package com.sharkula.gusle.ui.albums

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Album
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.albumKey
import com.sharkula.gusle.ui.songs.SongGroupsScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The [albums] A–Z, each with its artist, and "Unknown album" last for the songs without one. The
 * album of the player's song is marked like the song itself.
 */
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    /** The player's song, playing or paused; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    onAlbum: (Album) -> Unit,
) {
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val variousArtists = stringResource(R.string.various_artists)
    // Null for the songs without an album, as for their row.
    val playingKey = playing?.let(::albumKey)

    SongGroupsScreen(
        title = stringResource(R.string.albums),
        groups = albums,
        // No album's key is empty, so the songs without one can have that.
        key = { it.key.orEmpty() },
        // Its first song's, in track order.
        coverOf = { it.songs.first() },
        label = { it.name ?: unknownAlbum },
        secondaryLabel = { if (it.variousArtists) variousArtists else it.artist ?: unknownArtist },
        noCoverIcon = R.drawable.ic_album,
        hasPlayingSong = { playing != null && it.key == playingKey },
        isPlaying = isPlaying,
        onGroup = onAlbum,
    )
}

private val previewAlbums = listOf(
    Album(
        "test ensemble\nmorning songs",
        "Morning Songs",
        "Test Ensemble",
        variousArtists = false,
        listOf(
            Song(1, Uri.EMPTY, "Mountain Song", "Test Ensemble", 314_000, 0),
            Song(3, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
        ),
    ),
    Album(
        "test trio\ngusle test songs",
        "Gusle test songs",
        "Test Trio",
        variousArtists = false,
        listOf(Song(2, Uri.EMPTY, "Old Road", "Test Trio", 125_000, 0)),
    ),
    Album(null, null, null, variousArtists = false, listOf(Song(4, Uri.EMPTY, "Untagged song", null, 45_000, 0))),
)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AlbumsScreenPreview() {
    GusleTheme {
        val playing = previewAlbums[0].songs[1].copy(album = "Morning Songs")
        AlbumsScreen(previewAlbums, playing = playing, isPlaying = true, onAlbum = {})
    }
}
