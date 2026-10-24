package com.sharkula.gusle.ui.songs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.Equalizer
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.formatLength
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.SongCoverCircle
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter

/**
 * A [song]'s row in a list: its cover, its title, and the [secondaryLabel] under it, after a small
 * heart if it [isFavorite]. The player's song is highlighted, with its equalizer at the end;
 * [isPlaying] is null for every other song. [noCover] stands in for missing covers
 * ([rememberRowNoCoverPainter], made once per list). Holding it opens the song's actions
 * ([onActions]), which screen readers offer as [actionsLabel] ([songActionsLabel], looked up
 * once per list).
 */
@Composable
fun TransformingLazyColumnItemScope.SongRow(
    song: Song,
    secondaryLabel: String?,
    isPlaying: Boolean?,
    noCover: Painter,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
    onActions: () -> Unit,
    actionsLabel: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = ButtonDefaults.Height,
    isFavorite: Boolean = false,
) {
    CoverRow(
        SongCover(song),
        song.title,
        secondaryLabel,
        isPlaying,
        noCover,
        transformationSpec,
        onClick,
        modifier,
        minHeight,
        isFavorite,
        onLongClick = onActions,
        onLongClickLabel = actionsLabel,
    )
}

/** What holding a song's row does, as screen readers offer it: "Open song options". */
@Composable
fun songActionsLabel(): String = stringResource(R.string.open_song_options)

/** A favorite song's heart on its row, before the line under the title. */
private val FavoriteMarkSize = 10.dp
private val FavoriteMarkGap = 3.dp

/**
 * A row in a list with a song's [cover] in its circle: a song's own row ([SongRow]), or e.g. an
 * artist's, with one of their songs' covers; [noCover]'s icon when there's no song, e.g. for an
 * empty playlist (null). Its [label] and the [secondaryLabel] under it end with
 * "…" when they don't fit; a favorite song's ([isFavorite]) has a small heart before the latter.
 * While [isPlaying] isn't null, the row is highlighted, with the equalizer at the end: for the
 * player's song, and for what it's from. [noCover] stands in for missing covers
 * ([rememberRowNoCoverPainter], made once per list). Holding it calls [onLongClick], which screen
 * readers name [onLongClickLabel].
 */
@Composable
fun TransformingLazyColumnItemScope.CoverRow(
    cover: SongCover?,
    label: String,
    secondaryLabel: String?,
    isPlaying: Boolean?,
    noCover: Painter,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ButtonDefaults.Height,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    // Accessibility services say what the equalizer shows.
    val state = isPlaying?.let { stringResource(if (it) R.string.playing else R.string.paused) }
    ListButton(
        onClick = onClick,
        transformationSpec = transformationSpec,
        modifier = if (state != null) modifier.semantics { stateDescription = state } else modifier,
        colors = if (isPlaying != null) {
            ButtonDefaults.filledVariantButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = { SongCoverCircle(cover, noCover) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondaryLabel != null || isFavorite) {
            {
                if (isFavorite) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        // Said with the row, between the title and the line under it.
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier.align(Alignment.CenterVertically).size(FavoriteMarkSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    if (secondaryLabel != null) Spacer(Modifier.width(FavoriteMarkGap))
                }
                if (secondaryLabel != null) Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            null
        },
        trailing = if (isPlaying != null) {
            { Equalizer(isPlaying) }
        } else {
            null
        },
        minHeight = minHeight,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
    )
}

/** How long the song is, e.g. 3:31; null rather than "0:00" when MediaStore doesn't know it. */
val Song.length: String? get() = if (durationMs > 0) formatLength(durationMs) else null
