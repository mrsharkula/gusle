package com.sharkula.gusle.ui.songs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.lazy.TransformationSpec
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter

/**
 * A list of groups of songs under its [title], e.g. Artists, Albums or Playlists. Each group's row
 * has the cover of one of its songs ([coverOf]; [noCoverIcon] while it loads, when there's none, or
 * when the group has no song), its [label], and the [secondaryLabel] under it. The group the player's
 * song is in ([hasPlayingSong]) is marked like the song itself, so the way back to it is easy to
 * find. [first] goes before the groups, e.g. New playlist where a song is added to one, and an
 * [edgeButton] hugs the bottom, e.g. New playlist on the Playlists list. Where [onGroupActions]
 * isn't null, holding a row opens the group's actions, e.g. a playlist's, which screen readers offer
 * as [groupActionsLabel].
 */
@Composable
fun <G : Any> SongGroupsScreen(
    title: String,
    groups: List<G>,
    /** Tells the rows apart, and keeps a row's place as the list changes. */
    key: (G) -> Any,
    coverOf: (G) -> Song?,
    label: @Composable (G) -> String,
    secondaryLabel: @Composable (G) -> String,
    @DrawableRes noCoverIcon: Int,
    /** Whether the player's song, playing or paused, is in the group. */
    hasPlayingSong: (G) -> Boolean,
    isPlaying: Boolean,
    onGroup: (G) -> Unit,
    first: (@Composable TransformingLazyColumnItemScope.(TransformationSpec) -> Unit)? = null,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    onGroupActions: ((G) -> Unit)? = null,
    groupActionsLabel: String? = null,
) {
    val noCover = rememberRowNoCoverPainter(noCoverIcon)

    ListScreen(title = title, edgeButton = edgeButton) { transformationSpec ->
        if (first != null) item(key = "first") { first(transformationSpec) }
        items(groups, key = key, contentType = { "group" }) { group ->
            CoverRow(
                coverOf(group)?.let(::SongCover),
                label = label(group),
                secondaryLabel = secondaryLabel(group),
                // Null for every row but the player's song's group's, as in a song list.
                isPlaying = if (hasPlayingSong(group)) isPlaying else null,
                noCover = noCover,
                transformationSpec = transformationSpec,
                onClick = { onGroup(group) },
                onLongClick = onGroupActions?.let { { it(group) } },
                onLongClickLabel = groupActionsLabel,
            )
        }
    }
}
