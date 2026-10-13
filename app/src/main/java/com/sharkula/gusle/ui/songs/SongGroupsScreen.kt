package com.sharkula.gusle.ui.songs

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.items
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter

/**
 * A list of groups of songs under its [title], e.g. Artists or Albums. Each group's row has the
 * cover of one of its songs ([coverOf]; [noCoverIcon] while it loads or when there's none), its
 * [label], and the [secondaryLabel] under it. The group the player's song is in ([hasPlayingSong])
 * is marked like the song itself, so the way back to it is easy to find.
 */
@Composable
fun <G : Any> SongGroupsScreen(
    title: String,
    groups: List<G>,
    /** Tells the rows apart, and keeps a row's place as the list changes. */
    key: (G) -> Any,
    coverOf: (G) -> Song,
    label: @Composable (G) -> String,
    secondaryLabel: @Composable (G) -> String,
    @DrawableRes noCoverIcon: Int,
    /** Whether the player's song, playing or paused, is in the group. */
    hasPlayingSong: (G) -> Boolean,
    isPlaying: Boolean,
    onGroup: (G) -> Unit,
) {
    val noCover = rememberRowNoCoverPainter(noCoverIcon)

    ListScreen(title = title) { transformationSpec ->
        items(groups, key = key, contentType = { "group" }) { group ->
            CoverRow(
                SongCover(coverOf(group)),
                label = label(group),
                secondaryLabel = secondaryLabel(group),
                // Null for every row but the player's song's group's, as in a song list.
                isPlaying = if (hasPlayingSong(group)) isPlaying else null,
                noCover = noCover,
                transformationSpec = transformationSpec,
                onClick = { onGroup(group) },
            )
        }
    }
}
