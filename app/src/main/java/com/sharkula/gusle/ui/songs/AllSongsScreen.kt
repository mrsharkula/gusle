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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter
import com.sharkula.gusle.ui.theme.GusleTheme

/** Every song on the watch, A–Z, with Shuffle all on top. The player's song is marked. */
@Composable
fun AllSongsScreen(
    songs: List<Song>,
    /** The player's song, playing or paused; null when there's none. */
    playing: Song?,
    isPlaying: Boolean,
    onShuffleAll: () -> Unit,
    /** Gets the tapped song's position in [songs]. */
    onSong: (index: Int) -> Unit,
    /** The player's song was tapped: it goes on as it is. */
    onPlayingSong: () -> Unit,
) {
    val noCover = rememberRowNoCoverPainter()

    ListScreen(title = pluralStringResource(R.plurals.song_count, songs.size, songs.size)) { transformationSpec ->
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
            SongRow(
                song,
                // Left out rather than "0:00" when MediaStore doesn't know it.
                secondaryLabel = if (song.durationMs > 0) formatLength(song.durationMs) else null,
                isPlaying = isPlayingHere,
                noCover = noCover,
                transformationSpec = transformationSpec,
                onClick = if (isPlayingHere != null) onPlayingSong else ({ onSong(index) }),
            )
        }
    }
}

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

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AllSongsScreenPreview() {
    val songs = listOf(
        Song(1, Uri.EMPTY, "A Very Long Song Title That Runs Past the Edge", "Test Trio", 249_000, 0),
        Song(2, Uri.EMPTY, "Length unknown", "Test Ensemble", 0, 0),
        Song(3, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
    )
    GusleTheme {
        AllSongsScreen(songs, playing = songs[2], isPlaying = true, onShuffleAll = {}, onSong = {}, onPlayingSong = {})
    }
}
