package com.sharkula.gusle.playback

import android.app.PendingIntent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sharkula.gusle.GusleApp
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.musicLibrary
import com.sharkula.gusle.nowPlayingIntent
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

private const val Tag = "PlaybackService"

/** While playing, where it is is saved this often, so a crash or a dead battery loses little. */
private const val SaveEveryMs = 10_000L

/**
 * While it plays on, the notification is updated this long after the first update asked for,
 * with every one asked for meanwhile (see [PlaybackService.onUpdateNotificationAsync]): past a song
 * change's busy first two seconds on the watch, when Now playing shows the new cover (up to ~1.3 s)
 * and the player fills its buffer (~1.6–2 s), and after the screens below catch up (StackScene's
 * CatchUpDelayMs and CatchUpStaggerMs: 2.4 s, 2.7 s). Counted from the first update rather than the
 * last, so skipping song after song can't put it off for long.
 */
private const val NotificationUpdateDelayMs = 3_200L

/**
 * Asks the service to stop and forget what it played, as if nothing had: Stop playing in Settings.
 * Only Gusle's screens may send it.
 */
val StopAndForget = SessionCommand("com.sharkula.gusle.STOP_AND_FORGET", Bundle.EMPTY)

/**
 * Plays the songs, also in the background, and shows them in the watch's media controls. The
 * screens connect to it with a MediaController and ask for songs by their [mediaId]s. It saves the
 * queue it plays ([LastPlayed]), and comes back to it, paused, when it starts again. Songs it can't
 * play are skipped ([SessionPlayer]).
 *
 * Swiping Gusle away from the recent apps stops the service unless it's playing (that's what
 * MediaSessionService's own onTaskRemoved does), once nothing is connected to it anymore. Only
 * Gusle's screens and the system connect, and the screens let go when Gusle leaves the screen.
 */
class PlaybackService : MediaSessionService() {
    private val scope = MainScope()
    private var session: MediaSession? = null

    /** The session's player, made as the service starts. */
    private lateinit var sessionPlayer: SessionPlayer

    /** The same as a Player, whose methods are stable API, unlike ForwardingPlayer's. */
    private val player: Player get() = sessionPlayer

    private val playing get() = (application as GusleApp).playing
    private val lastPlayed get() = (application as GusleApp).lastPlayed
    private val unplayable get() = (application as GusleApp).unplayable
    private val tileUpdates get() = (application as GusleApp).tileUpdates
    private var savingWhilePlaying: Job? = null
    private var notificationUpdate: Job? = null
    private var updatingNotification = false

    /**
     * Whether the service is in the foreground, as the last notification update that went out left
     * it: as asked until it's done, and then as it really is.
     */
    private var notificationInForeground = false

    /**
     * Tells the screens what's playing, so they can show it without connecting, and saves it to
     * come back to.
     */
    private val followPlayer = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            playing.update(player)
            if (
                events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                save()
            } else if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                // The timeline also changes as each song loads, which changes nothing worth saving.
                // Where the songs came from changes only with them.
                save(onlyNewQueue = true)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) saveWhilePlaying()
            // A jump within the song: the tile's time goes on from somewhere else. Song changes and
            // play/pause reach the tile through the song shown ([PlayingSongHolder]).
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) tileUpdates.changed()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            // Handling audio focus pauses for calls and when another app starts playing.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pauses when headphones disconnect, rather than going on through the speaker. The
            // speaker itself is allowed, so nothing suppresses playback on it.
            .setHandleAudioBecomingNoisy(true)
            // Keeps playing with the screen off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        sessionPlayer = SessionPlayer(
            exoPlayer,
            unplayable,
            describe = { source ->
                queueMetadata(source.name(musicLibrary.songs.value as? Songs.Loaded, resources), source)
            },
        )
        player.addListener(followPlayer)
        val lookup = SongLookup(musicLibrary, lastPlayed, packageName, scope, stopAndForget = { stopAndForget(player) })
        session = MediaSession.Builder(this, player)
            // Tapping the watch's media controls opens Gusle on Now playing, or brings it back there
            // if it's open.
            .setSessionActivity(
                PendingIntent.getActivity(this, 0, nowPlayingIntent(), PendingIntent.FLAG_IMMUTABLE),
            )
            .setCallback(lookup)
            .build()
        // Back to the queue it had last, paused and not prepared, so no song is read until it plays.
        // With the songs and the save read already (started from the screens), that's done before
        // anything connects; otherwise once they're read. A command that reaches the player first
        // is newer, so then it stays.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val queue = lookup.lastQueue()?.takeIf { player.mediaItemCount == 0 } ?: return@launch
            queue.saved.run { sessionPlayer.restore(queue.items, source, current, positionMs, shuffleOrder) }
        }
        // Its queue goes by the names the songs give now, e.g. once an artist is spelled another way.
        scope.launch { musicLibrary.songs.collect { sessionPlayer.nameQueue() } }
        playing.update(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /**
     * Media3 updates the notification for each change in the player: about five times for a new
     * song, each time also asking to stay in the foreground. On the watch, that was 34 of the ~62
     * binder calls a song change made on the main thread, which Now playing draws on too, so it
     * dropped frames (measured 2026-10-10). While it plays on in the foreground, the updates asked
     * for go out as one, [NotificationUpdateDelayMs] after the first; it still shows the newest
     * state. Anything else goes out at once: going into the foreground, as Android requires, and
     * pausing or stopping, for which Media3 asks to stay in the foreground for a while yet.
     */
    @OptIn(UnstableApi::class) // onUpdateNotificationAsync
    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ): ListenableFuture<Void?> {
        // Not Media3's isPlaybackOngoing: it stays true once the service has been in the foreground.
        if (startInForegroundRequired && notificationInForeground && session.player.playsOn && !updatingNotification) {
            if (notificationUpdate == null) {
                notificationUpdate = scope.launch {
                    delay(NotificationUpdateDelayMs)
                    notificationUpdate = null
                    // Back here with what Media3 asks for now, which this time goes out.
                    updatingNotification = true
                    try {
                        triggerNotificationUpdate()
                    } finally {
                        updatingNotification = false
                    }
                }
            }
            return Futures.immediateVoidFuture()
        }
        // The newest state, so one waiting isn't needed anymore.
        notificationUpdate?.cancel()
        notificationUpdate = null
        notificationInForeground = startInForegroundRequired
        val update = super.onUpdateNotificationAsync(session, startInForegroundRequired)
        // Once it's done, whether the service really is in the foreground: the tile waits for that
        // before it lets go of a song it started. Not what was asked for: Android may refuse the
        // foreground to a service started in the background, and Media3 takes the notification
        // down instead when there's nothing to show, e.g. with no song.
        update.addListener(
            {
                if (!update.isCancelled) {
                    try {
                        update.get()
                    } catch (e: ExecutionException) {
                        Log.w(Tag, "Couldn't update the notification", e.cause)
                    }
                }
                // No type (0) while it's out of the foreground.
                val inForeground = foregroundServiceType != 0
                // Otherwise, after a refused start, the next updates would wait to go out as one.
                notificationInForeground = inForeground
                playing.inForeground(inForeground)
            },
            ContextCompat.getMainExecutor(this),
        )
        return update
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.removeListener(followPlayer)
            save()
            playing.serviceStopped()
            player.release()
            unplayable.serviceStopped()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Saves the queue the player has; with none, the one saved last stays. With [onlyNewQueue], only
     * if its songs, their order or where they came from changed.
     */
    private fun save(onlyNewQueue: Boolean = false) {
        val queue = player.savedQueue(sessionPlayer.queueSource) ?: return
        val last = lastPlayed.saved
        if (onlyNewQueue && last != null && queue.sameSongsAs(last)) return
        lastPlayed.save(queue)
    }

    /**
     * Stops playing and forgets the queue, as if nothing had played, and stops the service, which
     * takes Gusle out of the watch's media controls once the screens let go of it.
     */
    @OptIn(UnstableApi::class) // pauseAllPlayersAndStopSelf
    private fun stopAndForget(player: Player) {
        // Forgotten first: told the player is empty, the screens would show the saved song instead.
        lastPlayed.forget()
        player.stop()
        player.clearMediaItems()
        pauseAllPlayersAndStopSelf()
    }

    private fun saveWhilePlaying() {
        savingWhilePlaying?.cancel()
        savingWhilePlaying = if (player.isPlaying) {
            scope.launch {
                while (true) {
                    delay(SaveEveryMs)
                    save()
                }
            }
        } else {
            null
        }
    }
}

/** The queue this player has, whose songs came from [source], or null if it has none. */
private fun Player.savedQueue(source: QueueSource): SavedQueue? {
    if (mediaItemCount == 0) return null
    val songIds = List(mediaItemCount) { getMediaItemAt(it).songId ?: return null }
    return SavedQueue(
        songIds,
        shuffleOrder = if (shuffleModeEnabled) currentTimeline.playOrder(shuffled = true) else null,
        current = currentMediaItemIndex,
        // After the last song has ended, it comes back from that song's start, not its very end.
        positionMs = if (playbackState == Player.STATE_ENDED) 0 else currentPosition,
        source = source,
    )
}

/** A saved queue as far as its songs are still on the watch, with the items that play them. */
private class RestoredQueue(val saved: SavedQueue, val items: List<MediaItem>)

/**
 * Lets in Gusle's screens and the system, and turns the song IDs they send into items the player
 * can play. It also looks up the queue the player had last, and runs [stopAndForget] when Gusle's
 * screens send [StopAndForget]. Callbacks run on the main thread, as the library needs.
 */
private class SongLookup(
    private val library: MusicLibrary,
    private val lastPlayed: LastPlayed,
    private val gusle: String,
    private val scope: CoroutineScope,
    private val stopAndForget: () -> Unit,
) : MediaSession.Callback {
    @OptIn(UnstableApi::class) // isTrusted, AcceptedResultBuilder
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): ConnectionResult {
        val isGusle = controller.packageName == gusle
        // Trusted: the system, e.g. the watch's media controls. Anyone else could find out which
        // songs are on the watch by asking to play IDs, without being allowed to read music.
        if (!isGusle && !controller.isTrusted) return ConnectionResult.reject()
        return ConnectionResult.AcceptedResultBuilder(session, controller)
            // Songs play once through; nothing may turn repeat on.
            .setAvailablePlayerCommands(
                ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon().remove(Player.COMMAND_SET_REPEAT_MODE).build(),
            )
            .apply {
                if (isGusle) {
                    setAvailableSessionCommands(
                        ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(StopAndForget).build(),
                    )
                }
            }
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction != StopAndForget.customAction) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        stopAndForget()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = withSongs { songs -> mediaItems.toPlayable(songs) }

    @OptIn(UnstableApi::class)
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> = withSongs { songs ->
        // Noting where the songs came from, so the player takes that with them, once they're found.
        val items = mediaItems.toPlayable(songs).comingFrom(QueueSource.of(mediaItems))
        if (startIndex == C.INDEX_UNSET) {
            MediaItemsWithStartPosition(items, C.INDEX_UNSET, startPositionMs)
        } else {
            // Songs deleted since the screen listed them are left out, so the start moves back by
            // those before it: it stays on the asked-for song, or the next one if that's gone too,
            // and never runs past the end. The position only applies to the song it was meant for.
            val start = mediaItems.take(startIndex).count { songs.lookUp(it) != null }
                .coerceAtMost(items.lastIndex)
            val startSongThere = mediaItems.getOrNull(startIndex)?.let { songs.lookUp(it) } != null
            MediaItemsWithStartPosition(items, start, if (startSongThere) startPositionMs else 0)
        }
    }

    /**
     * The queue the player had last, as far as its songs are still on the watch; null if there's
     * nothing to come back to. Waits until the songs are read and there are some, e.g. for the
     * screens' next read when this one fails. When none of its songs is left, it's forgotten.
     */
    suspend fun lastQueue(): RestoredQueue? {
        val songs = songsOnWatch()
        // Read after the songs, so it's the newest: something may have been saved meanwhile.
        val saved = lastPlayed.load() ?: return null
        val left = saved.keepOnly(songs::containsKey)
        if (left == null) lastPlayed.forget()
        return left?.let { queue -> RestoredQueue(queue, queue.songIds.map { songs.getValue(it).toMediaItem() }) }
    }

    /**
     * Runs [block] with the songs on the watch (see [songs]). The returned future fails when they
     * can't be read, or [block] throws; then the player is left as it was.
     */
    private fun <T> withSongs(block: (Map<Long, Song>) -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        // Undispatched: with the songs read already, it's done before it's returned.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                result.set(block(songs()))
            } catch (e: IllegalArgumentException) {
                result.setException(e)
            }
        }
        return result
    }

    /**
     * The songs on the watch. If they haven't been read yet (the service can start without the
     * screens), reads them first, or waits for the read going on. Throws IllegalArgumentException
     * when they can't be read.
     */
    private suspend fun songs(): Map<Long, Song> {
        (library.songs.value as? Songs.Loaded)?.let { return it.byId }
        // This read's result, not what's there before it ends (e.g. an earlier read that failed).
        library.readIfIdle().join()
        val songs = library.songs.value
        return (songs as? Songs.Loaded)?.byId ?: throw IllegalArgumentException("Can't read the songs ($songs)")
    }

    /**
     * The songs on the watch, once they're read and there are some. A read that fails or finds
     * none, e.g. while the watch is still indexing them, doesn't count: it waits for the next one.
     */
    private suspend fun songsOnWatch(): Map<Long, Song> {
        fun Songs.found() = (this as? Songs.Loaded)?.takeIf { it.list.isNotEmpty() }
        library.songs.value.found()?.let { return it.byId }
        library.readIfIdle()
        return library.songs.mapNotNull { it.found() }.first().byId
    }

    private fun Map<Long, Song>.lookUp(item: MediaItem) = item.songId?.let(::get)

    /**
     * The items that play the songs asked for; songs no longer on the watch are left out. If none
     * of them is there, throws rather than answering with nothing: that would empty the queue.
     */
    private fun List<MediaItem>.toPlayable(songs: Map<Long, Song>): List<MediaItem> {
        val playable = mapNotNull { songs.lookUp(it)?.toMediaItem() }
        require(playable.isNotEmpty() || isEmpty()) { "None of the songs asked for is on the watch" }
        return playable
    }
}
