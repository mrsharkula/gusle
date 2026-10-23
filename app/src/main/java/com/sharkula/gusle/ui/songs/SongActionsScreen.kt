package com.sharkula.gusle.ui.songs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.ambient.LocalAmbient
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.covers.CoverArt
import com.sharkula.gusle.ui.favorites.HeartIcon
import com.sharkula.gusle.ui.favorites.rememberHeartTap
import com.sharkula.gusle.ui.nowplaying.Cover
import com.sharkula.gusle.ui.nowplaying.DesignScale
import com.sharkula.gusle.ui.nowplaying.DimmedCoverAlpha
import com.sharkula.gusle.ui.nowplaying.artistColor
import com.sharkula.gusle.ui.nowplaying.designText
import com.sharkula.gusle.ui.nowplaying.rememberCoverPicture
import com.sharkula.gusle.ui.nowplaying.rememberStandInArt
import com.sharkula.gusle.ui.nowplaying.seeThroughLabelButtonColors
import com.sharkula.gusle.ui.theme.GusleTheme
import java.text.BreakIterator
import kotlinx.coroutines.flow.first

// The song actions screen, layout B of designs/v2/SONG-ACTIONS.md: the song's cover behind, its title
// and artist, and its actions as see-through buttons, which scroll over the cover while it stays.
// Delete is designs/v2/DELETE.md's.

/** A cover read after the screen opened comes up this gently, rather than all at once. */
private const val CoverFadeInMs = 250

/**
 * Around the title and artist: below the list's own top padding, the title starts 44 dp down, as in
 * the design, over the upper part of the cover; and the actions start a little below the artist.
 */
private val SongPadding = PaddingValues(start = 30.dp, top = 21.dp, end = 30.dp, bottom = 10.dp)

/** The actions are this tall and at least this wide, with their labels this far from the ends. */
private val ActionHeight = 48.dp
private val ActionMinWidth = 150.dp
private val ActionPadding = PaddingValues(horizontal = 16.dp)
private val ActionIconSize = 20.dp
private val ActionIconGap = 7.dp

/** Where "Couldn't delete" is in the list: after the song, the favorite and Delete. */
private const val CouldntDeleteItem = 3

/** "Couldn't delete" is lifted until it ends this far down the screen, clear of the round edge. */
private const val MessageBottom = 0.85f

/**
 * What can be done to a [song], opened by holding it anywhere: over its cover, dimmed, its title and
 * artist, and its actions: adding it to the favorites or removing it ([isFavorite], [onFavorite]; null
 * does nothing), and deleting it from the watch ([deletion]), which Gusle asks about first, then
 * Android. In the colors of the screen it came from, the playing song's. It stays open after the
 * favorite's tap, showing what it did; once the song is deleted, it closes. Gusle's question shows
 * only while the screen is [onTop], and goes once another screen opens over it.
 */
@Composable
fun SongActionsScreen(
    song: Song,
    isFavorite: Boolean,
    onFavorite: ((favorite: Boolean) -> Unit)?,
    deletion: SongDeletion,
    onTop: Boolean,
) {
    // A screen opened over it, e.g. Now playing from the watch's media controls, ends the question:
    // its dialog has a window of its own, which would stay over that screen.
    LaunchedEffect(onTop) { if (!onTop) deletion.dismiss() }
    SongActions(
        song.title,
        song.artist,
        rememberCoverPicture(song),
        isFavorite,
        onFavorite,
        deletion.step,
        onDelete = deletion::ask,
        onConfirmDelete = deletion::confirm,
        onDismissDelete = deletion::dismiss,
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
    /** Where deleting the song is: Gusle's question shows while it's [DeletionStep.Asking]. */
    deleteStep: DeletionStep,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    // Up at once when it's there in the first frame; faded in when it's read after.
    val coverShown = remember { Animatable(if (art != null) 1f else 0f) }
    LaunchedEffect(art != null) {
        if (art != null && coverShown.value < 1f) {
            if (reduceMotion) coverShown.snapTo(1f) else coverShown.animateTo(1f, tween(CoverFadeInMs))
        }
    }
    // "Couldn't delete" goes under Delete, which can be at the bottom edge already: it's lifted into
    // view. By a distance, as scrolling to it stops at the edge, where the list draws items smaller.
    val failed = deleteStep == DeletionStep.Failed
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        // Once the list has laid it out: until then, there's nothing to scroll to.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > CouldntDeleteItem }
        fun message() = listState.layoutInfo.visibleItems.firstOrNull { it.index == CouldntDeleteItem }
        if (message() == null) listState.scrollToItem(CouldntDeleteItem)
        val shown = message() ?: return@LaunchedEffect
        val by = shown.offset + shown.transformedHeight - listState.layoutInfo.viewportSize.height * MessageBottom
        if (by > 0) {
            if (reduceMotion) listState.scrollBy(by) else listState.animateScrollBy(by)
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        // Behind the list, and staying put as it scrolls; its glow, for a song without a cover, in
        // this screen's colors rather than the app theme's.
        if (art != null) {
            DesignScale {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Cover(art.picture, DimmedCoverAlpha, Modifier.graphicsLayer { alpha = coverShown.value }, noCoverColors = colors)
                }
            }
        }
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item(key = "song") {
                // Read out as one heading: "Bismarck, Sabaton".
                Column(
                    Modifier
                        .edgeTransformation(this, transformationSpec)
                        .fillMaxWidth()
                        .padding(SongPadding)
                        .semantics(mergeDescendants = true) { heading() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = designText(16, FontWeight.SemiBold, shadowAlpha = 0.75f, lineHeight = 20.sp),
                    )
                    if (!artist.isNullOrBlank()) {
                        Text(
                            artist,
                            color = artistColor(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f, lineHeight = 16.sp),
                        )
                    }
                }
            }
            item(key = "favorite") {
                val tap = rememberHeartTap()
                // See-through, like Now playing's play button.
                Button(
                    // Nothing, no tick, once the song is being deleted.
                    onClick = { onFavorite?.let { tap.tap(adding = !isFavorite, it) } },
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .widthIn(min = ActionMinWidth)
                        .heightIn(min = ActionHeight),
                    colors = seeThroughLabelButtonColors(),
                    contentPadding = ActionPadding,
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    HeartIcon(
                        isFavorite,
                        tap,
                        outlineColor = Color.White,
                        modifier = Modifier.align(Alignment.CenterVertically).size(ActionIconSize),
                    )
                    Spacer(Modifier.width(ActionIconGap))
                    Text(
                        stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        maxLines = 2,
                        style = actionLabelStyle(),
                    )
                }
            }
            item(key = "delete") {
                val label = stringResource(R.string.delete_from_watch)
                // See-through like the favorite: Android's own screen is what guards the file.
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .widthIn(min = ActionMinWidth)
                        .heightIn(min = ActionHeight)
                        // Says where from, which the short label leaves to the screen.
                        .semantics { contentDescription = label },
                    colors = seeThroughLabelButtonColors(),
                    contentPadding = ActionPadding,
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterVertically).size(ActionIconSize),
                    )
                    Spacer(Modifier.width(ActionIconGap))
                    Text(
                        stringResource(R.string.delete),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        maxLines = 2,
                        style = actionLabelStyle(),
                    )
                }
            }
            if (deleteStep == DeletionStep.Failed) {
                // Screen readers hear it as it happens (SongDeleter), so it isn't a live region too.
                item(key = "couldnt_delete") {
                    Text(
                        stringResource(R.string.couldnt_delete),
                        modifier = Modifier.edgeTransformation(this, transformationSpec).padding(top = 4.dp),
                        color = colors.error,
                        textAlign = TextAlign.Center,
                        style = designText(12, FontWeight.Normal, shadowAlpha = 0.85f, lineHeight = 16.sp),
                    )
                }
            }
        }
    }
    // Gusle's question, before Android's own: Wear's dialog, with the song's title, ✕ and ✓.
    // The dialog's window is its own, so Gusle's always-on display doesn't cover it: it hides
    // meanwhile, and comes back as the wrist comes up.
    AlertDialog(
        visible = deleteStep == DeletionStep.Asking && !LocalAmbient.current.isAmbient,
        onDismissRequest = onDismissDelete,
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = onConfirmDelete) },
        // Whole, past the dialog's usual three lines, which would cut "from the watch?" short; a long
        // song title is shortened instead.
        title = { Text(stringResource(R.string.delete_song_question, questionTitle(title)), maxLines = Int.MAX_VALUE) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = onDismissDelete) },
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
    )
}

/** The song's title in Gusle's question: at most this many characters. */
private const val QuestionTitleMax = 40

/**
 * [title] as Gusle's question names it: as it is, or cut to [QuestionTitleMax] characters with
 * "…", so the question stays a few lines long. Cut between characters as the reader sees them: never
 * inside an emoji, a flag, or a letter with its accent.
 */
internal fun questionTitle(title: String): String {
    if (title.length <= QuestionTitleMax) return title
    val characters = BreakIterator.getCharacterInstance().apply { setText(title) }
    val end = characters.preceding(QuestionTitleMax)
    return title.substring(0, end.coerceAtLeast(0)).trimEnd() + "…"
}

/** An action's label, over the cover. */
@Composable
private fun actionLabelStyle() =
    designText(13, FontWeight.SemiBold, shadowAlpha = 0.6f, shadowBlur = 3.dp, lineHeight = 17.sp)

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

@Composable
private fun SongActionsPreview(
    title: String,
    art: CoverArt,
    isFavorite: Boolean,
    deleteStep: DeletionStep = DeletionStep.None,
) {
    GusleTheme {
        AppScaffold {
            SongActions(
                title,
                artist = "Test Ensemble",
                art,
                isFavorite,
                onFavorite = {},
                deleteStep,
                onDelete = {},
                onConfirmDelete = {},
                onDismissDelete = {},
            )
        }
    }
}
