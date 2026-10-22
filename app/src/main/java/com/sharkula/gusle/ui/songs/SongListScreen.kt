package com.sharkula.gusle.ui.songs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Favorite
import com.sharkula.gusle.data.FavoriteSongs
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter
import com.sharkula.gusle.ui.favorites.FavoriteSwipe
import com.sharkula.gusle.ui.favorites.rememberSwipeRows
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * A list of songs under its [title], with Shuffle all on top: All songs, an artist's songs, or the
 * favorites. The player's song is marked, and with [heartsShown], the [favorites]. Tapping a song
 * plays it and then the rest of the list, in its order; swiping it left adds it to the favorites, or
 * removes it ([onFavorite]); holding it opens its actions ([onSongActions]).
 */
@Composable
fun SongListScreen(
    title: String,
    songs: List<Song>,
    /** The player's song, playing or paused; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    favorites: FavoriteSongs,
    onShuffleAll: () -> Unit,
    /** Gets the tapped song's position in [songs]. */
    onSong: (index: Int) -> Unit,
    /** The player's song was tapped: it goes on as it is. */
    onPlayingSong: () -> Unit,
    onFavorite: (song: Song, favorite: Boolean) -> Unit,
    onSongActions: (Song) -> Unit,
    /** False where every song is a favorite: in Favorites itself. */
    heartsShown: Boolean = true,
) {
    val noCover = rememberRowNoCoverPainter()
    val actionsLabel = songActionsLabel()
    val listState = rememberTransformingLazyColumnState()
    val swipeRows = rememberSwipeRows(listState)

    ListScreen(title = title, listState = listState) { transformationSpec ->
        item(key = "shuffle all") {
            ListButton(
                onClick = onShuffleAll,
                transformationSpec = transformationSpec,
                label = {
                    // Centered, unlike the song rows, so it reads as the screen's action.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shuffle_all))
                    }
                },
            )
        }
        itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "song" }) { index, song ->
            // Null for every row but the player's song's, so when the player changes, only the
            // rows it changes for are composed again.
            val isPlayingHere = if (song.id == playing?.id) isPlaying else null
            val isFavorite = song in favorites
            FavoriteSwipe(
                song.id,
                isFavorite,
                onToggle = { onFavorite(song, it) },
                swipeRows,
                transformationSpec,
            ) { actions ->
                SongRow(
                    song,
                    secondaryLabel = song.length,
                    isPlaying = isPlayingHere,
                    noCover = noCover,
                    transformationSpec = transformationSpec,
                    onClick = if (isPlayingHere != null) onPlayingSong else ({ onSong(index) }),
                    onActions = { onSongActions(song) },
                    actionsLabel = actionsLabel,
                    modifier = actions,
                    isFavorite = heartsShown && isFavorite,
                )
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SongListScreenPreview() {
    val songs = listOf(
        Song(1, Uri.EMPTY, "A Very Long Song Title That Runs Past the Edge", "Test Trio", 249_000, 0),
        Song(2, Uri.EMPTY, "Length unknown", "Test Ensemble", 0, 0),
        Song(3, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
    )
    GusleTheme {
        SongListScreen(
            pluralStringResource(R.plurals.song_count, songs.size, songs.size),
            songs,
            playing = songs[2],
            isPlaying = true,
            favorites = FavoriteSongs(mapOf(3L to Favorite(addedAtMs = 0, durationMs = null))),
            onShuffleAll = {},
            onSong = {},
            onPlayingSong = {},
            onFavorite = { _, _ -> },
            onSongActions = {},
        )
    }
}
