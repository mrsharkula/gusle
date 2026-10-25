package com.sharkula.gusle.ui.playlists

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Playlist
import com.sharkula.gusle.data.PlaylistEntry
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playlists
import com.sharkula.gusle.ui.components.ActionEdgeButton
import com.sharkula.gusle.ui.components.MessageScreen
import com.sharkula.gusle.ui.heldWhileHidden
import com.sharkula.gusle.ui.songs.SongGroupsScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The playlists, for a screen to show: as they are while it can be seen, a moment later while it's
 * hidden below another one ([heldWhileHidden]). Null until they've been read, which is done as the
 * app starts.
 */
@Composable
fun rememberPlaylists(): List<Playlist>? {
    val playlists by LocalContext.current.playlists.all.collectAsStateWithLifecycle()
    return heldWhileHidden(playlists)
}

/**
 * The [playlists], oldest first, each with its first song's cover (the list icon when none of its
 * songs is on the watch), its name, and how many of its songs are ([songsById]). The playlist the
 * player's queue came from ([playingFrom]) is marked like an artist with the player's song. Holding a
 * playlist opens its actions ([onPlaylistActions]). New playlist is the edge button. With no
 * playlists, a screen says how they're made, with New playlist.
 */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    /** The ID of the playlist the player's queue came from, playing or paused; null for none. */
    playingFrom: Long?,
    isPlaying: Boolean,
    onPlaylist: (Playlist) -> Unit,
    onPlaylistActions: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    // Once per list, not as each row is composed again, e.g. as the player plays or pauses.
    val rows = remember(playlists, songsById) { playlists.map { PlaylistRow(it, songsById) } }
    if (rows.isEmpty()) {
        MessageScreen(
            icon = R.drawable.ic_queue_music,
            title = stringResource(R.string.no_playlists_title),
            text = stringResource(R.string.add_to_playlist_hint),
            action = stringResource(R.string.new_playlist),
            onAction = onNewPlaylist,
            actionIcon = { AddIcon() },
            actionIconGap = AddIconGap,
        )
        return
    }
    SongGroupsScreen(
        title = stringResource(R.string.playlists),
        groups = rows,
        key = { it.playlist.id },
        coverOf = { it.firstSong },
        label = { it.playlist.name },
        secondaryLabel = { pluralStringResource(R.plurals.song_count, it.songCount, it.songCount) },
        noCoverIcon = R.drawable.ic_queue_music,
        hasPlayingSong = { it.playlist.id == playingFrom },
        isPlaying = isPlaying,
        onGroup = { onPlaylist(it.playlist) },
        // As the empty screen's, so it stays the same once there's a playlist.
        edgeButton = {
            ActionEdgeButton(stringResource(R.string.new_playlist), onNewPlaylist, icon = { AddIcon() }, AddIconGap)
        },
        onGroupActions = { onPlaylistActions(it.playlist) },
        groupActionsLabel = stringResource(R.string.open_playlist_options),
    )
}

/**
 * A playlist's row: its first song on the watch ([songsById]), for the cover, how many it has there,
 * and how often a [song] is in it (0 without one), for the list of playlists to add it to.
 */
internal class PlaylistRow(val playlist: Playlist, songsById: Map<Long, Song>, song: Song? = null) {
    val firstSong = playlist.firstSongIn(songsById)
    val songCount = playlist.countIn(songsById)
    val timesOfSong = song?.let(playlist::timesOf) ?: 0
}

/**
 * The "+" before "New playlist" in the edge button, and the gap after it: smaller than the other
 * edge buttons' icons (16 dp, 6 dp), so "New playlist" fits on one line at the default font size.
 * The plus has room around it in its square anyway.
 */
private val AddIconSize = 12.dp
private val AddIconGap = 2.dp

@Composable
private fun AddIcon() {
    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(AddIconSize))
}

private val previewSongs = listOf(
    Song(1, Uri.EMPTY, "Mountain Song", "Test Ensemble", 314_000, 0),
    Song(2, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
).associateBy { it.id }

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PlaylistsScreenPreview() {
    GusleTheme {
        PlaylistsScreen(
            listOf(
                Playlist(1, "Road trip", listOf(PlaylistEntry(1, 2, 211_000), PlaylistEntry(2, 1, 314_000))),
                Playlist(2, "Workout", emptyList()),
            ),
            previewSongs,
            playingFrom = 1,
            isPlaying = true,
            onPlaylist = {},
            onPlaylistActions = {},
            onNewPlaylist = {},
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PlaylistsScreenEmptyPreview() {
    GusleTheme {
        PlaylistsScreen(
            emptyList(),
            previewSongs,
            playingFrom = null,
            isPlaying = false,
            onPlaylist = {},
            onPlaylistActions = {},
            onNewPlaylist = {},
        )
    }
}
