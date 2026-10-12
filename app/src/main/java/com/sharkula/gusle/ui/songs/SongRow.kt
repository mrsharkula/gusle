package com.sharkula.gusle.ui.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.Equalizer
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.SongCoverCircle
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter

/**
 * A [song]'s row in a list: its cover, its title, and the [secondaryLabel] under it. The player's
 * song is highlighted, with its equalizer at the end; [isPlaying] is null for every other song.
 * [noCover] stands in for missing covers ([rememberRowNoCoverPainter], made once per list).
 */
@Composable
fun TransformingLazyColumnItemScope.SongRow(
    song: Song,
    secondaryLabel: String?,
    isPlaying: Boolean?,
    noCover: Painter,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ButtonDefaults.Height,
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
    )
}

/**
 * A row in a list with a song's [cover] in its circle: a song's own row ([SongRow]), or e.g. an
 * artist's, with one of their songs' covers. Its [label] and the [secondaryLabel] under it end with
 * "…" when they don't fit. While [isPlaying] isn't null, the row is highlighted, with the equalizer
 * at the end: for the player's song, and for what it's from. [noCover] stands in for missing covers
 * ([rememberRowNoCoverPainter], made once per list).
 */
@Composable
fun TransformingLazyColumnItemScope.CoverRow(
    cover: SongCover,
    label: String,
    secondaryLabel: String?,
    isPlaying: Boolean?,
    noCover: Painter,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ButtonDefaults.Height,
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
        secondaryLabel = if (secondaryLabel != null) {
            { Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailing = if (isPlaying != null) {
            { Equalizer(isPlaying) }
        } else {
            null
        },
        minHeight = minHeight,
    )
}

/** How long the song is, e.g. 3:31; null rather than "0:00" when MediaStore doesn't know it. */
val Song.length: String? get() = if (durationMs > 0) formatLength(durationMs) else null

/** 3:31, or 1:02:05 from an hour on. Built by hand: it runs for every row that scrolls in. */
private fun formatLength(ms: Long): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    return buildString {
        if (hours > 0) {
            append(hours).append(':')
            appendTwoDigits(seconds / 60 % 60)
        } else {
            append(seconds / 60)
        }
        append(':')
        appendTwoDigits(seconds % 60)
    }
}

private fun StringBuilder.appendTwoDigits(value: Long) {
    if (value < 10) append('0')
    append(value)
}
