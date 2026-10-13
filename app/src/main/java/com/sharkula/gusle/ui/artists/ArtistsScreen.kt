package com.sharkula.gusle.ui.artists

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Artist
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.artistKey
import com.sharkula.gusle.ui.songs.SongGroupsScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The [artists] A–Z, each with how many songs they have, and "Unknown artist" last for the songs
 * without one. The artist of the player's song is marked like the song itself.
 */
@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    /** The player's song, playing or paused; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    onArtist: (Artist) -> Unit,
) {
    val unknownArtist = stringResource(R.string.unknown_artist)
    // Null for the songs without an artist, as for their row.
    val playingKey = playing?.artist?.let(::artistKey)

    SongGroupsScreen(
        title = stringResource(R.string.artists),
        groups = artists,
        // No artist's key is empty, so the songs without one can have that.
        key = { it.key.orEmpty() },
        // Their first song's, A–Z.
        coverOf = { it.songs.first() },
        label = { it.name ?: unknownArtist },
        secondaryLabel = { pluralStringResource(R.plurals.song_count, it.songs.size, it.songs.size) },
        noCoverIcon = R.drawable.ic_person,
        hasPlayingSong = { playing != null && it.key == playingKey },
        isPlaying = isPlaying,
        onGroup = onArtist,
    )
}

private val previewArtists = listOf(
    Artist(
        "Test Ensemble",
        listOf(
            Song(1, Uri.EMPTY, "Mountain Song", "Test Ensemble", 314_000, 0),
            Song(3, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
        ),
    ),
    Artist("Test Trio", listOf(Song(2, Uri.EMPTY, "No Cover Here", "Test Trio", 125_000, 0))),
    Artist(null, listOf(Song(4, Uri.EMPTY, "Untagged song", null, 45_000, 0))),
)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun ArtistsScreenPreview() {
    GusleTheme {
        ArtistsScreen(previewArtists, playing = previewArtists[0].songs[1], isPlaying = true, onArtist = {})
    }
}
