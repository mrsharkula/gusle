package com.sharkula.gusle.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Playlist
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.nowplaying.rememberCoverPicture
import com.sharkula.gusle.ui.nowplaying.rememberStandInArt
import com.sharkula.gusle.ui.songs.ActionButton
import com.sharkula.gusle.ui.songs.ActionsScreen
import com.sharkula.gusle.ui.songs.DeleteQuestion
import com.sharkula.gusle.ui.theme.GusleTheme

// A playlist's actions, like a song's (designs/v2/PROTOTYPE.md, decision 7; designs/v2/PLAYLISTS.md,
// build 3): its first song's cover behind, its name and song count, Rename and Delete.

/** Behind a playlist none of whose songs is on the watch: the glow, at once. */
private val NoSongArt = CoverArt(null, null, null)

/**
 * What can be done to a [playlist], opened by holding it on the Playlists list: over its first song's
 * cover ([songsById]), dimmed, or the glow with the list icon where there's none, its name and how many
 * of its songs are on the watch; then Rename ([onRename]), and Delete, which Gusle asks about first
 * ([onDelete] once that's a yes). In the colors of the screen it came from, the playing song's. The
 * question shows only while the screen is [onTop], and goes once another screen opens over it.
 */
@Composable
fun PlaylistActionsScreen(
    playlist: Playlist,
    songsById: Map<Long, Song>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTop: Boolean,
) {
    // Kept when Gusle is recreated, as a song's question is.
    var asking by rememberSaveable { mutableStateOf(false) }
    // A screen opened over it, e.g. Now playing from the watch's media controls, ends the question:
    // its dialog has a window of its own, which would stay over that screen.
    LaunchedEffect(onTop) { if (!onTop) asking = false }
    val row = remember(playlist, songsById) { PlaylistRow(playlist, songsById) }
    // Read either way, so a first song coming back takes over from the glow, rather than leaving
    // nothing behind until its cover is read.
    val picture = rememberCoverPicture(row.firstSong)
    PlaylistActions(
        playlist.name,
        row.songCount,
        art = if (row.firstSong != null) picture else NoSongArt,
        asking = asking,
        onRename = onRename,
        onDelete = { asking = true },
        onConfirmDelete = {
            if (asking) {
                asking = false
                onDelete()
            }
        },
        onDismissDelete = { asking = false },
    )
}

/** The screen as drawn in the design, given the first song's [art] once it's read (null until then). */
@Composable
private fun PlaylistActions(
    name: String,
    songCount: Int,
    art: CoverArt?,
    /** Whether Gusle's question shows. */
    asking: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    ActionsScreen(
        name,
        subtitle = pluralStringResource(R.plurals.song_count, songCount, songCount),
        art,
        rememberTransformingLazyColumnState(),
        noCoverIcon = R.drawable.ic_queue_music,
    ) { transformationSpec ->
        item(key = "rename") {
            ActionButton(stringResource(R.string.rename), onRename, transformationSpec) { modifier ->
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = modifier)
            }
        }
        item(key = "delete") {
            ActionButton(stringResource(R.string.delete), onDelete, transformationSpec) { modifier ->
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = modifier)
            }
        }
    }
    // With the playlist's name, and what stays.
    DeleteQuestion(
        visible = asking,
        R.string.delete_playlist_question,
        name,
        onConfirm = onConfirmDelete,
        onDismiss = onDismissDelete,
        text = stringResource(R.string.delete_playlist_text),
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PlaylistActionsPreview() {
    PlaylistActionsPreview("Road trip", 5, rememberStandInArt())
}

/** Empty, with a long name. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PlaylistActionsEmptyPreview() {
    PlaylistActionsPreview("A very long playlist name that runs past the edge", 0, NoSongArt)
}

/** Gusle's question. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun PlaylistActionsAskingPreview() {
    PlaylistActionsPreview("Road trip", 5, rememberStandInArt(), asking = true)
}

@Composable
private fun PlaylistActionsPreview(name: String, songCount: Int, art: CoverArt, asking: Boolean = false) {
    GusleTheme {
        AppScaffold {
            PlaylistActions(
                name,
                songCount,
                art,
                asking,
                onRename = {},
                onDelete = {},
                onConfirmDelete = {},
                onDismissDelete = {},
            )
        }
    }
}
