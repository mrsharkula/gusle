package com.sharkula.gusle.ui.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.playback.PlaybackService
import com.sharkula.gusle.playback.toMediaItemRequest
import java.util.concurrent.ExecutionException

private const val Tag = "Player"

/**
 * Gusle's playback service as the screens use it. It connects on the first tap rather than at
 * launch, so just browsing never starts the player, and runs the tap once connected. It lets
 * go when Gusle leaves the screen, and connects again on the next tap after that, or after the
 * connection failed or was lost.
 */
class PlayerConnection(private val context: Context) {
    private var connecting: ListenableFuture<MediaController>? = null
    private var connected: MediaController? = null

    /** Runs [action] with the player, connecting first if needed. */
    fun withPlayer(action: Player.() -> Unit) {
        val future = connecting ?: connect().also { connecting = it }
        // The future completes on the main thread, so this runs right then, or right away if it
        // already has. When Gusle left the screen first, the future is cancelled and the tap
        // goes with it.
        future.addListener(
            {
                if (!future.isCancelled) {
                    try {
                        future.get().action()
                    } catch (e: ExecutionException) {
                        Log.w(Tag, "Couldn't connect to the playback service", e)
                        if (connecting === future) connecting = null
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Lets go of the player, e.g. when Gusle leaves the screen. */
    fun release() {
        val future = connecting ?: return
        connecting = null
        connected = null
        MediaController.releaseFuture(future)
    }

    private fun connect(): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    // E.g. the system stopped the service: the next tap connects again.
                    override fun onDisconnected(controller: MediaController) {
                        if (controller === connected) release()
                    }
                },
            )
            .buildAsync()
        future.addListener(
            { if (!future.isCancelled) connected = runCatching { future.get() }.getOrNull() },
            MoreExecutors.directExecutor(),
        )
        return future
    }
}

/** The connection to the player for this screen; it lets go when Gusle leaves the screen. */
@Composable
fun rememberPlayerConnection(): PlayerConnection {
    val context = LocalContext.current
    val connection = remember(context) { PlayerConnection(context) }
    LifecycleStartEffect(connection) {
        onStopOrDispose { connection.release() }
    }
    return connection
}

/**
 * Plays the song at [start] and then the [songs] after it, in their order (A–Z). The songs above
 * it stay in the queue, so Previous goes back to them.
 */
fun Player.playFrom(songs: List<Song>, start: Int) {
    shuffleModeEnabled = false
    setMediaItems(songs.map { it.toMediaItemRequest() }, start, /* startPositionMs = */ 0)
    prepare()
    play()
}

/** Plays all [songs] in a new random order, starting at a random one. */
fun Player.shuffleAll(songs: List<Song>) {
    // Shuffle goes on first: then setting the songs draws a new random order, and playback starts
    // at its first song.
    shuffleModeEnabled = true
    setMediaItems(songs.map { it.toMediaItemRequest() }, /* resetPosition = */ true)
    prepare()
    play()
}
