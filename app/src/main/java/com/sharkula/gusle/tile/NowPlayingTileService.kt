package com.sharkula.gusle.tile

import android.content.ComponentName
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.tiles.EventBuilders.TileAddEvent
import androidx.wear.tiles.EventBuilders.TileRemoveEvent
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sharkula.gusle.GusleApp
import com.sharkula.gusle.MainActivity
import com.sharkula.gusle.OpenNowPlayingExtra
import com.sharkula.gusle.coverColors
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.jumpStepChoice
import com.sharkula.gusle.playback.PlaybackService
import com.sharkula.gusle.playback.PlayerState
import com.sharkula.gusle.playback.QueueSource
import com.sharkula.gusle.playback.knownDurationMs
import com.sharkula.gusle.playback.songId
import com.sharkula.gusle.themeChoice
import com.sharkula.gusle.ui.playback.shuffleAll
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val Tag = "NowPlayingTile"

/**
 * A tap on the tile's buttons gives up after this long: the system waits about 10 s for the tile,
 * and then shows nothing new. It covers starting the playback service, reading the songs, coming
 * back to the saved song and going into the foreground, which together take about a second.
 */
private const val TapTimeoutMs = 7_000L

/**
 * After a playing song's end, the tile is made again this much later, by the system: the song
 * after it is playing by then, or nothing is.
 */
private const val AfterSongEndMs = 1_500L

/**
 * Gusle's tile, one swipe from the watch face: the song the player has, or had last, with
 * play/pause, previous and next, and jumps back and on by the step chosen in Settings
 * ([com.sharkula.gusle.ui.nowplaying.JumpStep]); tapping the song opens Now playing. With no song
 * saved, it offers Shuffle all. See designs/notes/TILE.md.
 *
 * The system asks for the tile when it's added, when Gusle asks for an update ([TileUpdates]), when
 * the playing song ends ([Tile.Builder.setFreshnessIntervalMillis]), and for a tap on a button. It
 * reads what's playing without starting the playback service, as the screens do; only a button
 * starts it. The time ticks on the watch's own clock, so the tile doesn't wake Gusle for it.
 */
class NowPlayingTileService : TileService() {
    private val scope = MainScope()
    private val app: GusleApp get() = application as GusleApp

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> = future {
        val clicked = requestParams.currentState.lastClickableId
        val songs = app.library.songsForTile()
        val favoriteTap = FavoriteTap.of(clicked)
        val jumpTap = JumpTap.of(clicked)
        val tapped = when {
            favoriteTap != null -> setFavorite(favoriteTap, songs)
            jumpTap != null -> withTimeoutOrNull(TapTimeoutMs) { jump(jumpTap, songs) }
            else -> TileButton.of(clicked)?.let { withTimeoutOrNull(TapTimeoutMs) { tap(it, songs) } }
        }
        val (tile, look) = tile(requestParams.deviceConfiguration, songs, tapped ?: app.playing.state())
        app.tileUpdates.shown(requestParams.tileId, look)
        tile
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> = future {
        TileResources.make(this@NowPlayingTileService, requestParams.version) { cover ->
            app.tileUpdates.coverUnread(cover, requestParams.version)
        }
    }

    override fun onTileAddEvent(requestParams: TileAddEvent) {
        app.tileUpdates.added(requestParams.tileId)
    }

    override fun onTileRemoveEvent(requestParams: TileRemoveEvent) {
        app.tileUpdates.removed(requestParams.tileId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** The tile for [player], the player's state, from [songs], and how it looks. */
    private suspend fun tile(device: DeviceParameters, songs: Songs, player: PlayerState?): Pair<Tile, TileLook> {
        // Both start reading the disk as they're made, so neither read waits for the other's.
        val themes = themeChoice
        val jumpSteps = jumpStepChoice
        val theme = themes.load()
        val jumpStep = jumpSteps.load()
        val favorites = app.favorites.load()
        var content = app.tileUpdates.versioned(TileContent.of(player, songs, favorites, tileCoverSizePx(), Instant.now()))
        // The cover's colors, worked out from the picture read at the size the tile and Now playing
        // show it at when they aren't known yet. A cover that can't be read now is left out, rather
        // than kept by the watch as none, and tried again.
        val cover = content.cover
        val ownColors = cover?.let { coverColors.known(it) ?: coverColors.forTile(it, content.coverSizePx).colors }
        if (cover != null && ownColors == null) {
            content = content.withoutCover()
            app.tileUpdates.coverUnread(cover)
        }
        val shows = content.shows
        val leftMs = (shows as? TileShows.Song)?.takeIf { it.isPlaying }?.let { it.durationMs - it.positionMs }
        val tile = Tile.Builder()
            .setResourcesVersion(content.resourcesVersion)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    tileLayout(this, device, shows, ownColors?.scheme ?: theme.colors, launches(), jumpStep),
                ),
            )
            // While a song plays, made again once it has ended; otherwise nothing changes until Gusle
            // asks for an update.
            .setFreshnessIntervalMillis(if (leftMs != null && leftMs > 0) leftMs + AfterSongEndMs else 0)
            .build()
        return tile to TileLook.of(content, theme, jumpStep)
    }

    /**
     * What tapping the song, and the title or the message, do: open Gusle on Now playing, as the
     * watch's media controls do, or as its launcher icon does (through an alias). This launches
     * MainActivity itself; Now playing is asked for with an extra that holds Gusle's key, so no
     * other app can ask for it. Neither the alias that other apps can't reach nor Gusle's own
     * PendingIntent works from a tile: the host can't launch the alias, as it isn't exported (tried
     * 2026-10-16, emulator, renderer 1.500), and the watch's host never got the PendingIntent
     * ("PendingIntent is canceled or invalid", OnePlus Watch 4, renderer 1.600).
     */
    private suspend fun launches(): TileLaunches {
        val key = app.nowPlayingKey
        val launchGusle = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()
        val launchNowPlaying = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(
                        OpenNowPlayingExtra,
                        // Read from disk the first time in a process.
                        ActionBuilders.AndroidStringExtra.Builder()
                            .setValue(withContext(Dispatchers.IO) { key.value() })
                            .build(),
                    )
                    .build(),
            )
            .build()
        return TileLaunches(nowPlaying = clickable(launchNowPlaying, "now_playing"), home = clickable(launchGusle, "home"))
    }

    /**
     * Makes the song the tile showed a favorite, or not, as its heart offered ([tap]): Gusle's own,
     * so the playback service isn't started for it. Returns the player's state, as [tap] does.
     */
    private suspend fun setFavorite(tap: FavoriteTap, songs: Songs): PlayerState? {
        (songs as? Songs.Loaded)?.byId?.get(tap.songId)?.let { song ->
            app.favorites.load()
            app.favorites.set(song, tap.favorite)
        }
        return app.playing.state()
    }

    /**
     * Does what [button] says to the playback service's player ([withRestoredPlayer]). Starting a
     * song also waits for the service to be in the foreground: until then, letting go of it would
     * stop it, and the song with it.
     */
    @OptIn(UnstableApi::class) // handlePlayButtonAction, handlePauseButtonAction
    private suspend fun tap(button: TileButton, songs: Songs): PlayerState? = withRestoredPlayer { player ->
        when (button) {
            TileButton.Play -> Util.handlePlayButtonAction(player)
            TileButton.Pause -> Util.handlePauseButtonAction(player)
            TileButton.Previous -> player.seekToPrevious()
            TileButton.Next -> player.seekToNext()
            TileButton.ShuffleAll -> (songs as? Songs.Loaded)?.list?.takeIf { it.isNotEmpty() }?.let { list ->
                player.shuffleAll(list, QueueSource.AllSongs)
            }
        }
        // Only a song starting puts the service in the foreground: with nothing to play, e.g. no
        // songs to shuffle, there's nothing to wait for.
        if ((button == TileButton.Play || button == TileButton.ShuffleAll) && player.startsPlaying) {
            awaitForeground(player)
        }
    }

    /** Jumps as [tap] says, by the step the tile showed ([withRestoredPlayer]). */
    private suspend fun jump(tap: JumpTap, songs: Songs): PlayerState? = withRestoredPlayer { player ->
        player.jump(tap.byMs, songs)
    }

    /**
     * Runs [block] on the playback service's player, starting the service if it isn't running, and
     * returns the player's state afterwards: what the tile shows next. Null when the service
     * couldn't be reached. A started service comes back to the saved song by itself, so that's
     * awaited first.
     */
    private suspend fun withRestoredPlayer(block: suspend (Player) -> Unit): PlayerState? = withController { player ->
        if (app.playing.load() != null) player.await { mediaItemCount > 0 }
        block(player)
        player.state()
    }

    /**
     * Waits until the playback service is in the foreground, or until [player] stops trying to
     * play, e.g. when none of its songs could be read: then the service doesn't go there.
     */
    private suspend fun awaitForeground(player: Player) = coroutineScope {
        val inForeground = async { app.playing.inForeground.first { it } }
        val gaveUp = async { player.await { !startsPlaying } }
        select {
            inForeground.onAwait {}
            gaveUp.onAwait {}
        }
        coroutineContext.cancelChildren()
    }

    /** Runs [block] with a controller of the playback service, which starts it if it isn't running. */
    private suspend fun <T> withController(block: suspend (Player) -> T): T? {
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .buildAsync()
        return try {
            val controller = future.awaitOrNull { e -> Log.w(Tag, "Couldn't connect to the playback service", e) }
            controller?.let { block(it) }
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    /** Runs [block] as the tile's answer, on the main thread. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                result.set(block())
            } catch (e: CancellationException) {
                result.cancel(false)
                throw e
            } catch (e: Exception) {
                // The system then keeps the tile, or the resources, it has, rather than waiting.
                Log.e(Tag, "Couldn't answer the tile's request", e)
                result.setException(e)
            }
        }
        return result
    }
}

/**
 * Whether this player plays, or is about to: what makes Media3 put the playback service in the
 * foreground.
 */
private val Player.startsPlaying: Boolean
    get() = playWhenReady && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)

/**
 * Jumps [byMs] on, or back if it's negative, within the song. By the song's index: a song only
 * restored, not yet prepared, can't be sought in otherwise (see the seek screen).
 */
private fun Player.jump(byMs: Long, songs: Songs) {
    val durationMs = knownDurationMs
        ?: currentMediaItem?.songId?.let { (songs as? Songs.Loaded)?.byId?.get(it)?.durationMs }
        ?: return
    seekTo(currentMediaItemIndex, (currentPosition + byMs).coerceIn(0, durationMs))
}

/** Waits until [isThere] holds for this player. */
private suspend fun Player.await(isThere: Player.() -> Boolean) {
    if (isThere()) return
    suspendCancellableCoroutine { continuation ->
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player.isThere() && continuation.isActive) {
                    player.removeListener(this)
                    continuation.resume(Unit)
                }
            }
        }
        addListener(listener)
        continuation.invokeOnCancellation { removeListener(listener) }
    }
}
