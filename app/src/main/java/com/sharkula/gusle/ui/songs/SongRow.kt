package com.sharkula.gusle.ui.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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
) {
    // Accessibility services say what the equalizer shows.
    val state = isPlaying?.let { stringResource(if (it) R.string.playing else R.string.paused) }
    ListButton(
        onClick = onClick,
        transformationSpec = transformationSpec,
        modifier = if (state != null) Modifier.semantics { stateDescription = state } else Modifier,
        colors = if (isPlaying != null) {
            ButtonDefaults.filledVariantButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = { SongCoverCircle(SongCover(song), noCover) },
        label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    )
}
