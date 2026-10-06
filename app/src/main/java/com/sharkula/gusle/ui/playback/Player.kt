package com.sharkula.gusle.ui.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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

/** After connecting fails, a screen that shows the player tries again this much later. */
private const val RetryDelayMs = 1000L

/**
 * Gusle's playback service as the screens use it. It connects on the first tap rather than at
 * launch, so just browsing never starts the player, and runs the tap once connected. A screen that
 * shows the player keeps it connected while it's on (see [rememberShownPlayer]). It lets go when
 * Gusle leaves the screen, and connects again the next time it's needed.
 */
class PlayerConnection(private val context: Context) {
    private var connecting: ListenableFuture<MediaController>? = null
    private val connected = mutableStateOf<MediaController?>(null)
    private var screensShowing = 0
    private val handler = Handler(Looper.getMainLooper())
    private val retry = Runnable { if (screensShowing > 0) connection() }

    /** The player once connected; null until then and after letting go. */
    val player: State<Player?> get() = connected

    /** Whether [player] is the one connected now, rather than one let go of. Compose observes it. */
    fun isConnected(player: Player): Boolean = connected.value === player

    /** Runs [action] with the player, connecting first if needed. */
    fun withPlayer(action: Player.() -> Unit) {
        val future = connection()
        // The future completes on the main thread, so this runs right then, or right away if it
        // already has. When Gusle left the screen first, the future is cancelled and the tap
        // goes with it; when connecting failed, the tap is dropped too.
        future.addListener(
            {
                if (!future.isCancelled) {
                    try {
                        future.get().action()
                    } catch (e: ExecutionException) {
                        // Logged where the connection is made.
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Keeps connected for a screen that shows the player, until [stopShowing]: connects now, again
     * right away when the connection is lost, and again a moment later when connecting fails.
     */
    fun startShowing() {
        screensShowing++
        connection()
    }

    fun stopShowing() {
        screensShowing--
        if (screensShowing == 0) handler.removeCallbacks(retry)
    }

    /** Lets go of the player, e.g. when Gusle leaves the screen. */
    fun release() {
        handler.removeCallbacks(retry)
        val future = connecting ?: return
        connecting = null
        // Cleared first: letting go calls onDisconnected too, which mustn't take it for a lost
        // connection.
        connected.value = null
        MediaController.releaseFuture(future)
    }

    private fun connection(): ListenableFuture<MediaController> =
        connecting ?: connectNow().also { connecting = it }

    private fun connectNow(): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    // E.g. the system stopped the service.
                    override fun onDisconnected(controller: MediaController) {
                        if (controller === connected.value) {
                            release()
                            // Otherwise the next tap connects again.
                            if (screensShowing > 0) connection()
                        }
                    }
                },
            )
            .buildAsync()
        future.addListener(
            {
                if (!future.isCancelled) {
                    try {
                        connected.value = future.get()
                    } catch (e: ExecutionException) {
                        Log.w(Tag, "Couldn't connect to the playback service", e)
                        if (connecting === future) {
                            connecting = null
                            // Otherwise the next tap tries again.
                            if (screensShowing > 0) handler.postDelayed(retry, RetryDelayMs)
                        }
                    }
                }
            },
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
 * The player, for a screen that shows it, which keeps it connected while the screen is on. Null
 * until it first connects. When Gusle comes back to the screen, it takes a moment to connect again;
 * meanwhile this is the one let go of, so the screen goes on showing what it showed rather than
 * flashing black. That one no longer changes or takes commands, so send them through
 * [PlayerConnection.withPlayer], which waits for the connection, and check
 * [PlayerConnection.isConnected] before reading anything anew.
 */
@Composable
fun PlayerConnection.rememberShownPlayer(): Player? {
    LifecycleStartEffect(this) {
        startShowing()
        onStopOrDispose { stopShowing() }
    }
    val connected = player.value
    val last = remember(this) { LastShown() }
    SideEffect { if (connected != null) last.player = connected }
    return connected ?: last.player
}

private class LastShown {
    var player: Player? = null
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
