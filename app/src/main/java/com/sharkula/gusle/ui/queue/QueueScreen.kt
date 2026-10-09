package com.sharkula.gusle.ui.queue

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.playOrder
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.covers.rememberRowNoCoverPainter
import com.sharkula.gusle.ui.playback.PlayerConnection
import com.sharkula.gusle.ui.playback.rememberShownPlayer
import com.sharkula.gusle.ui.songs.SongRow
import com.sharkula.gusle.ui.songs.length
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.unplayableSongs

/** The rows are a little lower than elsewhere, to fit more songs. */
private val RowHeight = 48.dp

/** The list's header and the shuffle toggle come before the songs. */
private const val FirstSongItem = 2

/** Songs played before the player's song, and songs that can't be played. */
private const val DimmedAlpha = 0.45f

/**
 * Fades each thing the row draws, rather than drawing the row offscreen and fading that: cheaper,
 * and where the cover and text overlap the row's background, the difference doesn't show.
 */
private val Dimmed = Modifier.graphicsLayer {
    alpha = DimmedAlpha
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The player's songs in the order they play, under a shuffle toggle; opened from Now playing. It
 * opens on the player's song, with the songs played before it above, dimmed, and it follows the
 * player wherever it's controlled from. Tapping a song plays it, in the same order; tapping the
 * player's song goes back to Now playing ([onPlayingSong]).
 */
@OptIn(UnstableApi::class) // rememberPlayPauseButtonState, rememberShuffleButtonState
@Composable
fun QueueScreen(connection: PlayerConnection, songsById: Map<Long, Song>, onPlayingSong: () -> Unit) {
    val player = connection.rememberShownPlayer()
    // Just the clock until the player first connects, which takes milliseconds.
    if (player == null) {
        ScreenScaffold {}
        return
    }
    val state = rememberQueueState(player, songsById)
    // As on Now playing, so the equalizer agrees with its play/pause button.
    val playPause = rememberPlayPauseButtonState(player)
    val shuffle = rememberShuffleButtonState(player)
    val unplayable by LocalContext.current.unplayableSongs.songIds.collectAsStateWithLifecycle()
    Queue(
        queue = state.queue,
        songsById = songsById,
        current = state.current,
        isPlaying = !playPause.showPlay,
        shuffleOn = shuffle.shuffleOn,
        unplayable = unplayable,
        // Through the connection, like Now playing's controls: the player shown may be one let go of.
        onShuffle = { connection.withPlayer { shuffleModeEnabled = !shuffleModeEnabled } },
        onSong = { window ->
            connection.withPlayer {
                seekToDefaultPosition(window)
                Util.handlePlayButtonAction(this)
            }
        },
        onPlayingSong = onPlayingSong,
    )
}

/** [player]'s queue and where it is, which follows the player while this is composed. */
@Composable
private fun rememberQueueState(player: Player, songsById: Map<Long, Song>): QueueState {
    val state = remember(player, songsById) { QueueState(player, songsById) }
    DisposableEffect(state) {
        player.addListener(state)
        // Whatever changed before it listened.
        state.update()
        onDispose { player.removeListener(state) }
    }
    return state
}

/**
 * The [player]'s queue ([PlayerQueue]) as far as its songs are among [songsById], and the item it's
 * on. The queue is built again only when the songs or their order change, not as the player moves
 * from song to song.
 */
@Stable
private class QueueState(private val player: Player, private val songsById: Map<Long, Song>) : Player.Listener {
    // What the queue was built from.
    private var timeline = player.currentTimeline
    private var shuffled = player.shuffleModeEnabled

    var queue by mutableStateOf(build())
        private set
    var current by mutableIntStateOf(player.currentMediaItemIndex)
        private set

    override fun onEvents(player: Player, events: Player.Events) = update()

    /** Takes what changed in the player. */
    fun update() {
        current = player.currentMediaItemIndex
        val newTimeline = player.currentTimeline
        val newShuffled = player.shuffleModeEnabled
        if (newTimeline === timeline && newShuffled == shuffled) return
        // Turned on, shuffle plays a new order with the song it's on first, which the playback
        // service sends a moment later as a new timeline. Until then, this connection has the old
        // order, which isn't shown. No new timeline comes when the new order is the old one, which
        // then starts with the song already.
        val oldOrder = newTimeline === timeline && newTimeline.getFirstWindowIndex(true) != player.currentMediaItemIndex
        if (newShuffled && !shuffled && oldOrder) return
        timeline = newTimeline
        shuffled = newShuffled
        // Equal to the last when the timeline changed only as a song loaded: then nothing is composed again.
        queue = build()
    }

    private fun build() =
        PlayerQueue.of(
            timeline.playOrder(shuffled),
            shuffled,
            songIdAt = { player.getMediaItemAt(it).songId },
            onWatch = songsById::containsKey,
        )
}

@Composable
private fun Queue(
    queue: PlayerQueue,
    /** The songs on the watch, which include every song in the [queue]. */
    songsById: Map<Long, Song>,
    /** The player's item it's on. */
    current: Int,
    isPlaying: Boolean,
    shuffleOn: Boolean,
    /** The songs that couldn't be played, by [Song.id]. */
    unplayable: Set<Long>,
    onShuffle: () -> Unit,
    /** Gets the player's item to jump to. */
    onSong: (window: Int) -> Unit,
    onPlayingSong: () -> Unit,
) {
    val currentPlace = queue.placeOf(current)
    // The player's song in the middle from the first frame, with the songs played before it above.
    val listState = rememberTransformingLazyColumnState(
        initialAnchorItemIndex = remember {
            queue.songs.indexOfFirst { it.window == current }.let { if (it < 0) -1 else FirstSongItem + it }
        },
    )
    // When the songs come in a shuffled order or A–Z, the list starts again at the top, with the
    // toggle where it was, rather than following the song in the middle to its new place.
    val shown = remember { ShownOrder(queue.shuffled) }
    SideEffect {
        if (shown.shuffled != queue.shuffled) {
            shown.shuffled = queue.shuffled
            listState.requestScrollToItem(0)
        }
    }
    val noCover = rememberRowNoCoverPainter()
    val cantPlay = stringResource(R.string.cant_play)

    // Where the queue comes from: in v1, always All songs.
    ListScreen(title = stringResource(R.string.all_songs), listState = listState) { transformationSpec ->
        item(key = "shuffle") {
            ShuffleToggle(shuffleOn, onShuffle, transformationSpec)
        }
        // By the player's item rather than the song, which could be in the queue twice.
        items(queue.songs, key = { it.window }, contentType = { "song" }) { entry ->
            val song = songsById.getValue(entry.songId)
            // Null for every row but the player's song's, so when the player moves on, only the rows
            // it changes for are composed again.
            val isPlayingHere = if (entry.window == current) isPlaying else null
            val canPlay = song.id !in unplayable
            SongRow(
                song,
                secondaryLabel = when {
                    !canPlay -> cantPlay
                    isPlayingHere != null -> song.artist
                    else -> song.length
                },
                isPlaying = isPlayingHere,
                noCover = noCover,
                transformationSpec = transformationSpec,
                // Rather than starting it again: on a watch, taps happen by accident.
                onClick = if (isPlayingHere != null) onPlayingSong else ({ onSong(entry.window) }),
                modifier = if (entry.place < currentPlace || !canPlay) Dimmed else Modifier,
                minHeight = RowHeight,
            )
        }
        item(key = "end") {
            Text(
                stringResource(R.string.end_of_list),
                modifier = Modifier.edgeTransformation(this, transformationSpec).fillMaxWidth().padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Whether the songs the list was last shown with came shuffled. */
private class ShownOrder(var shuffled: Boolean)

/**
 * "Shuffle on" filled in the primary color, or "Shuffle off" outlined; a tap switches it, and
 * accessibility services read it as the switch "Shuffle", on or off.
 */
@Composable
private fun TransformingLazyColumnItemScope.ShuffleToggle(
    on: Boolean,
    onToggle: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(R.string.shuffle)
    // One button for both, so it keeps its place for accessibility services as it switches.
    Box(Modifier.fillMaxWidth().transformedHeight(this, transformationSpec), contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier
                .height(40.dp)
                // In place of the button's own, which would read as a button with its label.
                .clearAndSetSemantics {
                    role = Role.Switch
                    contentDescription = name
                    toggleableState = ToggleableState(on)
                    onClick { onToggle(); true }
                },
            colors = if (on) {
                ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    iconColor = colors.onPrimary,
                )
            } else {
                ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface, iconColor = colors.onSurface)
            },
            border = if (on) null else BorderStroke(1.dp, colors.onSurfaceVariant),
            contentPadding = PaddingValues(horizontal = 16.dp),
            transformation = SurfaceTransformation(transformationSpec),
        ) {
            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (on) R.string.shuffle_on else R.string.shuffle_off),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

private val previewSongs = listOf(
    Song(1, Uri.EMPTY, "Mountain Song", "Test Ensemble", 314_000, 0),
    Song(2, Uri.EMPTY, "No Cover Here", "Test Trio", 125_000, 0),
    Song(3, Uri.EMPTY, "River at Dawn", "Test Ensemble", 211_000, 0),
)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun QueueShuffleOffPreview() {
    GusleTheme {
        Queue(
            queue = PlayerQueue.of(listOf(0, 1, 2), shuffled = false, { previewSongs[it].id }, onWatch = { true }),
            songsById = previewSongs.associateBy { it.id },
            current = 1,
            isPlaying = true,
            shuffleOn = false,
            unplayable = emptySet(),
            onShuffle = {},
            onSong = {},
            onPlayingSong = {},
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun QueueShuffleOnPreview() {
    GusleTheme {
        Queue(
            queue = PlayerQueue.of(listOf(1, 2, 0), shuffled = true, { previewSongs[it].id }, onWatch = { true }),
            songsById = previewSongs.associateBy { it.id },
            current = 1,
            isPlaying = true,
            shuffleOn = true,
            unplayable = setOf(3L),
            onShuffle = {},
            onSong = {},
            onPlayingSong = {},
        )
    }
}
