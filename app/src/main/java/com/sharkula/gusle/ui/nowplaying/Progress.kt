package com.sharkula.gusle.ui.nowplaying

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import com.sharkula.gusle.ui.playback.PlayerConnection
import kotlinx.coroutines.flow.collectLatest

/** Light on the battery, and still smooth: a step moves the ring a few pixels. */
private const val ProgressTickMs = 1000L

/**
 * The [player]'s progress, updated once a second while it plays, but only while it's connected
 * and [shown]: media3's rememberProgressStateWithTickInterval goes on ticking in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberProgress(
    connection: PlayerConnection,
    player: Player,
    shown: State<Boolean>,
): ProgressStateWithTickInterval {
    val scope = rememberCoroutineScope()
    val progress = remember(player) { ProgressStateWithTickInterval(player, ProgressTickMs, scope) }
    LaunchedEffect(progress, connection, shown) {
        // A player let go of reads 0 for everything; its connected successor takes over soon.
        snapshotFlow { shown.value && connection.isConnected(player) }
            .collectLatest { live -> if (live) progress.observe() }
    }
    return progress
}

/**
 * How long the song is. Until the song is prepared, e.g. when the player comes back to it paused,
 * the player doesn't know yet, so it's [songDurationMs] (MediaStore's); 0 when neither knows.
 */
@OptIn(UnstableApi::class)
fun ProgressStateWithTickInterval.durationOr(songDurationMs: Long): Long =
    durationMs.takeIf { it > 0 } ?: songDurationMs.coerceAtLeast(0)

/** How far it is through the song, from 0 to 1, of its length as [durationOr] has it. */
@OptIn(UnstableApi::class)
fun ProgressStateWithTickInterval.fraction(songDurationMs: Long): Float {
    val duration = durationOr(songDurationMs)
    return if (duration > 0) (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
}
