package com.sharkula.gusle.ui.playlists

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Playlist
import com.sharkula.gusle.data.PlaylistEntry
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.songs.SongGroupsScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The playlists a [song] can be added to, opened from its actions: New playlist first
 * ([onNewPlaylist]), then the [playlists], oldest first, each with its first song's cover, its name,
 * how many of its songs are on the watch ([songsById]), and how often the song is in it already. A
 * tap adds it ([onPlaylist]), again if it's there.
 */
@Composable
fun AddToPlaylistScreen(
    song: Song,
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    onPlaylist: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    // Once per list, as on the Playlists list.
    val rows = remember(playlists, songsById, song) { playlists.map { PlaylistRow(it, songsById, song) } }
    val colors = MaterialTheme.colorScheme

    SongGroupsScreen(
        title = stringResource(R.string.add_to_playlist),
        groups = rows,
        key = { it.playlist.id },
        coverOf = { it.firstSong },
        label = { it.playlist.name },
        secondaryLabel = { row ->
            val count = pluralStringResource(R.plurals.song_count, row.songCount, row.songCount)
            if (row.timesOfSong > 0) {
                pluralStringResource(R.plurals.song_count_in_it, row.timesOfSong, row.timesOfSong, count)
            } else {
                count
            }
        },
        noCoverIcon = R.drawable.ic_queue_music,
        // Where the song goes, not what's playing.
        hasPlayingSong = { false },
        isPlaying = false,
        onGroup = { onPlaylist(it.playlist) },
        first = { transformationSpec ->
            // Like Home's rows: an icon in the circle where the playlists have a cover.
            ListButton(
                onClick = onNewPlaylist,
                transformationSpec = transformationSpec,
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = {
                    IconCircle(R.drawable.ic_add, colors.secondaryContainer, tint = colors.onSecondaryContainer)
                },
                label = { Text(stringResource(R.string.new_playlist), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    )
}

private val previewSongs = listOf(
    Song(1, Uri.EMPTY, "Mountain Song", "Test Ensemble", 314_000, 0),
    Song(2, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
).associateBy { it.id }

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AddToPlaylistScreenPreview() {
    GusleTheme {
        AddToPlaylistScreen(
            previewSongs.getValue(2),
            listOf(
                Playlist(
                    1,
                    "Road trip",
                    listOf(PlaylistEntry(1, 2, 211_000), PlaylistEntry(2, 1, 314_000), PlaylistEntry(3, 2, 211_000)),
                ),
                Playlist(2, "Workout", listOf(PlaylistEntry(4, 1, 314_000))),
                Playlist(3, "Calm", emptyList()),
            ),
            previewSongs,
            onPlaylist = {},
            onNewPlaylist = {},
        )
    }
}
