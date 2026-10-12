package com.sharkula.gusle.ui.artists

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Artist
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.artistKey
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter
import com.sharkula.gusle.ui.songs.CoverRow
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The [artists] A–Z, each with how many songs they have, and "Unknown artist" last for the songs
 * without one. The artist of the player's song is marked like the song itself, so the way back to
 * it is easy to find.
 */
@Composable
fun ArtistsScreen(
    artists: List<Artist>,
    /** The player's song, playing or paused; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    onArtist: (Artist) -> Unit,
) {
    val noCover = rememberRowNoCoverPainter(R.drawable.ic_person)
    val unknownArtist = stringResource(R.string.unknown_artist)
    // Null for the songs without an artist, as for their row.
    val playingKey = playing?.artist?.let(::artistKey)

    ListScreen(title = stringResource(R.string.artists)) { transformationSpec ->
        // No artist's key is empty, so the songs without one can have that.
        items(artists, key = { it.key.orEmpty() }, contentType = { "artist" }) { artist ->
            // Null for every row but the player's song's artist's, as in a song list.
            val isPlayingHere = if (playing != null && artist.key == playingKey) isPlaying else null
            CoverRow(
                // The cover of their first song, A–Z.
                SongCover(artist.songs.first()),
                label = artist.name ?: unknownArtist,
                secondaryLabel = pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                isPlaying = isPlayingHere,
                noCover = noCover,
                transformationSpec = transformationSpec,
                onClick = { onArtist(artist) },
            )
        }
    }
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
