package com.sharkula.gusle.ui.songs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import coil3.compose.AsyncImage
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.components.RowCircleIconSize
import com.sharkula.gusle.ui.components.RowCircleSize
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.theme.GusleTheme

/** Every song on the watch, A–Z, with Shuffle all on top. */
@Composable
fun AllSongsScreen(
    songs: List<Song>,
    onShuffleAll: () -> Unit,
    onSong: (Song) -> Unit,
) {
    val noCover = rememberNoCoverPainter()

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
        items(songs, key = { it.id }, contentType = { "song" }) { song ->
            ListButton(
                onClick = { onSong(song) },
                transformationSpec = transformationSpec,
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = {
                    // Center-cropped, so a 16:9 cover shows its middle.
                    AsyncImage(
                        model = SongCover(song),
                        contentDescription = null,
                        modifier = Modifier.size(RowCircleSize).clip(CircleShape),
                        placeholder = noCover,
                        error = noCover,
                        contentScale = ContentScale.Crop,
                    )
                },
                label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // Left out rather than "0:00" when MediaStore doesn't know it.
                secondaryLabel = if (song.durationMs > 0) {
                    { Text(formatLength(song.durationMs)) }
                } else {
                    null
                },
            )
        }
    }
}

/** The music note in a list row's circle, while a cover loads and for songs without one. */
@Composable
private fun rememberNoCoverPainter(): Painter {
    val note = painterResource(R.drawable.ic_music_note)
    val colors = MaterialTheme.colorScheme
    return remember(note, colors) {
        NoCoverPainter(note, colors.secondaryContainer, colors.onSecondaryContainer)
    }
}

/** Fills its bounds (the image clips them to a circle) and centers the note in them. */
private class NoCoverPainter(
    private val note: Painter,
    private val background: Color,
    noteColor: Color,
) : Painter() {
    private val noteTint = ColorFilter.tint(noteColor)

    override val intrinsicSize get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(background)
        val noteSize = RowCircleIconSize.toPx()
        translate((size.width - noteSize) / 2, (size.height - noteSize) / 2) {
            with(note) { draw(Size(noteSize, noteSize), colorFilter = noteTint) }
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
        AllSongsScreen(songs, onShuffleAll = {}, onSong = {})
    }
}
