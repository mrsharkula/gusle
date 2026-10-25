package com.sharkula.gusle.ui.songs

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.ambient.LocalAmbient
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.components.lastNotNull
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.favorites.HeartIcon
import com.sharkula.gusle.ui.favorites.rememberHeartTap
import com.sharkula.gusle.ui.nowplaying.designText
import com.sharkula.gusle.ui.nowplaying.rememberCoverPicture
import com.sharkula.gusle.ui.nowplaying.rememberStandInArt
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlinx.coroutines.flow.first

// The song actions screen, layout B of designs/v2/SONG-ACTIONS.md: the song's cover behind, its title
// and artist, and its actions as see-through buttons, which scroll over the cover while it stays
// ([ActionsScreen]). Delete is designs/v2/DELETE.md's, the playlists' designs/v2/PLAYLISTS.md's.

/** "Added to …" stays this long (or longer, if accessibility settings ask), then goes by itself. */
private const val AddedShownMs = 1_200L

/** "Couldn't delete" is lifted until it ends this far down the screen, clear of the round edge. */
private const val MessageBottom = 0.85f

/**
 * What can be done to a [song], opened by holding it anywhere: over its cover, dimmed, its title and
 * artist, and its actions: adding it to the favorites or removing it ([isFavorite], [onFavorite]; null
 * does nothing), adding it to a playlist ([onAddToPlaylist]), taking it out of the one it was held in
 * ([onRemoveFromPlaylist]; null where it wasn't held in one), and deleting it from the watch
 * ([deletion]), which Gusle asks about first, then Android. In the colors of the screen it came from,
 * the playing song's. It stays open after the favorite's tap, showing what it did; once the song is
 * deleted, it closes. Back from adding it to a playlist, it says so for a moment ([addedTo], the
 * playlist's name; [onAddedShown] once that's done). Gusle's question and that moment show only while
 * the screen is [onTop], and go once another screen opens over it.
 */
@Composable
fun SongActionsScreen(
    song: Song,
    isFavorite: Boolean,
    onFavorite: ((favorite: Boolean) -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    deletion: SongDeletion,
    addedTo: String?,
    onAddedShown: () -> Unit,
    onTop: Boolean,
) {
    // A screen opened over it, e.g. Now playing from the watch's media controls, ends the question:
    // its dialog has a window of its own, which would stay over that screen. "Added to …" too, and
    // the always-on display, which that window would cover: it's only for a moment anyway.
    val isAmbient = LocalAmbient.current.isAmbient
    LaunchedEffect(onTop) { if (!onTop) deletion.dismiss() }
    LaunchedEffect(onTop, isAmbient) { if (!onTop || isAmbient) onAddedShown() }
    SongActions(
        song.title,
        song.artist,
        rememberCoverPicture(song),
        isFavorite,
        onFavorite,
        onAddToPlaylist,
        onRemoveFromPlaylist,
        deletion.step,
        onDelete = deletion::ask,
        onConfirmDelete = deletion::confirm,
        onDismissDelete = deletion::dismiss,
        addedTo = addedTo.takeIf { onTop && !isAmbient },
        onAddedShown = onAddedShown,
    )
}

/** The screen as drawn in the design, given the song's [art] once it's read (null until then). */
@Composable
private fun SongActions(
    title: String,
    artist: String?,
    art: CoverArt?,
    isFavorite: Boolean,
    onFavorite: ((favorite: Boolean) -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    /** Where deleting the song is: Gusle's question shows while it's [DeletionStep.Asking]. */
    deleteStep: DeletionStep,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    /** The playlist the song was just added to, said for a moment; null for nothing to say. */
    addedTo: String? = null,
    onAddedShown: () -> Unit = {},
) {
    val listState = rememberTransformingLazyColumnState()
    val reduceMotion = LocalReduceMotion.current
    // "Couldn't delete" goes under Delete, which can be at the bottom edge already: it's lifted into
    // view. By a distance, as scrolling to it stops at the edge, where the list draws items smaller.
    // It's the last item: after the song ([FirstActionIndex]), the favorite, Add to playlist, Remove
    // from this playlist if it's there, and Delete.
    val couldntDeleteItem = FirstActionIndex + if (onRemoveFromPlaylist != null) 4 else 3
    val failed = deleteStep == DeletionStep.Failed
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        // Once the list has laid it out: until then, there's nothing to scroll to.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > couldntDeleteItem }
        fun message() = listState.layoutInfo.visibleItems.firstOrNull { it.index == couldntDeleteItem }
        if (message() == null) listState.scrollToItem(couldntDeleteItem)
        val shown = message() ?: return@LaunchedEffect
        val by = shown.offset + shown.transformedHeight - listState.layoutInfo.viewportSize.height * MessageBottom
        if (by > 0) {
            if (reduceMotion) listState.scrollBy(by) else listState.animateScrollBy(by)
        }
    }

    ActionsScreen(title, subtitle = artist, art, listState) { transformationSpec ->
        item(key = "favorite") {
            val tap = rememberHeartTap()
            ActionButton(
                stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                // Nothing, no tick, once the song is being deleted.
                onClick = { onFavorite?.let { tap.tap(adding = !isFavorite, it) } },
                transformationSpec,
            ) { modifier ->
                HeartIcon(isFavorite, tap, outlineColor = Color.White, modifier = modifier)
            }
        }
        item(key = "add to playlist") {
            ActionButton(stringResource(R.string.add_to_playlist), onAddToPlaylist, transformationSpec) { modifier ->
                Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null, modifier = modifier)
            }
        }
        if (onRemoveFromPlaylist != null) {
            item(key = "remove from playlist") {
                ActionButton(stringResource(R.string.remove_from_playlist), onRemoveFromPlaylist, transformationSpec) { modifier ->
                    Icon(painterResource(R.drawable.ic_playlist_remove), contentDescription = null, modifier = modifier)
                }
            }
        }
        item(key = "delete") {
            val label = stringResource(R.string.delete_from_watch)
            // See-through like the others: Android's own screen is what guards the file.
            ActionButton(
                stringResource(R.string.delete),
                onDelete,
                transformationSpec,
                // Says where from, which the short label leaves to the screen.
                Modifier.semantics { contentDescription = label },
            ) { modifier ->
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = modifier)
            }
        }
        if (deleteStep == DeletionStep.Failed) {
            // Screen readers hear it as it happens (SongDeleter), so it isn't a live region too.
            item(key = "couldnt_delete") {
                Text(
                    stringResource(R.string.couldnt_delete),
                    modifier = Modifier.edgeTransformation(this, transformationSpec).padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f, lineHeight = 16.sp),
                )
            }
        }
    }
    // Gusle's question, before Android's own, with the song's title.
    DeleteQuestion(
        visible = deleteStep == DeletionStep.Asking,
        R.string.delete_song_question,
        title,
        onConfirm = onConfirmDelete,
        onDismiss = onDismissDelete,
    )
    // Wear's confirmation, back from adding the song to a playlist; a swipe right ends it sooner. It
    // keeps the name as it goes.
    val added = lastNotNull(addedTo)
    ConfirmationDialog(
        visible = addedTo != null,
        onDismissRequest = onAddedShown,
        text = { Text(stringResource(R.string.added_to_playlist, added.orEmpty())) },
        durationMillis = AddedShownMs,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize),
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsPreview() {
    SongActionsPreview(title = "River at Dawn", art = rememberStandInArt(), isFavorite = false)
}

/** Deleting it didn't work. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsCouldntDeletePreview() {
    SongActionsPreview("River at Dawn", rememberStandInArt(), isFavorite = false, deleteStep = DeletionStep.Failed)
}

/** Gusle's question. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsAskingPreview() {
    SongActionsPreview("River at Dawn", rememberStandInArt(), isFavorite = false, deleteStep = DeletionStep.Asking)
}

/** A favorite with a long title and no cover. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsNoCoverPreview() {
    SongActionsPreview(
        title = "A Very Long Song Title That Runs Past the Edge",
        art = CoverArt(null, null, null),
        isFavorite = true,
    )
}

/** Held inside a playlist. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsInPlaylistPreview() {
    SongActionsPreview("River at Dawn", rememberStandInArt(), isFavorite = false, inPlaylist = true)
}

/** Back from adding it to a playlist. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongActionsAddedPreview() {
    SongActionsPreview("River at Dawn", rememberStandInArt(), isFavorite = false, addedTo = "Road trip")
}

@Composable
private fun SongActionsPreview(
    title: String,
    art: CoverArt,
    isFavorite: Boolean,
    deleteStep: DeletionStep = DeletionStep.None,
    inPlaylist: Boolean = false,
    addedTo: String? = null,
) {
    GusleTheme {
        AppScaffold {
            SongActions(
                title,
                artist = "Test Ensemble",
                art,
                isFavorite,
                onFavorite = {},
                onAddToPlaylist = {},
                onRemoveFromPlaylist = if (inPlaylist) ({}) else null,
                deleteStep,
                onDelete = {},
                onConfirmDelete = {},
                onDismissDelete = {},
                addedTo = addedTo,
            )
        }
    }
}
