package com.sharkula.gusle.playback

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sharkula.gusle.GusleApp
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.musicLibrary
import com.sharkula.gusle.nowPlayingIntent
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** While playing, where it is is saved this often, so a crash or a dead battery loses little. */
private const val SaveEveryMs = 10_000L

/**
 * Plays the songs, also in the background, and shows them in the watch's media controls. The
 * screens connect to it with a MediaController and ask for songs by their [mediaId]s. It saves the
 * queue it plays ([LastPlayed]), and comes back to it, paused, when it starts again.
 *
 * Swiping Gusle away from the recent apps stops the service unless it's playing (that's what
 * MediaSessionService's own onTaskRemoved does), once nothing is connected to it anymore. Only
 * Gusle's screens and the system connect, and the screens let go when Gusle leaves the screen.
 */
class PlaybackService : MediaSessionService() {
    private val scope = MainScope()
    private var session: MediaSession? = null
    private val playing get() = (application as GusleApp).playing
    private val lastPlayed get() = (application as GusleApp).lastPlayed
    private var savingWhilePlaying: Job? = null

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
                save(player)
            } else if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                // The timeline also changes as each song loads, which changes nothing worth saving.
                save(player, onlyNewQueue = true)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) saveWhilePlaying(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
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
        player.addListener(followPlayer)
        val lookup = SongLookup(musicLibrary, lastPlayed, packageName, scope)
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
            val queue = lookup.lastQueue()
            if (queue != null && player.mediaItemCount == 0) player.restore(queue)
        }
        playing.update(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.removeListener(followPlayer)
            save(player)
            playing.serviceStopped()
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Saves the queue [player] has; with none, the one saved last stays. With [onlyNewQueue], only
     * if its songs or their order changed.
     */
    private fun save(player: Player, onlyNewQueue: Boolean = false) {
        val queue = player.savedQueue() ?: return
        val last = lastPlayed.saved
        if (onlyNewQueue && queue.songIds == last?.songIds && queue.shuffleOrder == last.shuffleOrder) return
        lastPlayed.save(queue)
    }

    private fun saveWhilePlaying(player: Player) {
        savingWhilePlaying?.cancel()
        savingWhilePlaying = if (player.isPlaying) {
            scope.launch {
                while (true) {
                    delay(SaveEveryMs)
                    save(player)
                }
            }
        } else {
            null
        }
    }
}

/** The queue this player has, or null if it has none. */
private fun Player.savedQueue(): SavedQueue? {
    if (mediaItemCount == 0) return null
    val songIds = List(mediaItemCount) { getMediaItemAt(it).songId ?: return null }
    return SavedQueue(
        songIds,
        shuffleOrder = if (shuffleModeEnabled) currentTimeline.shuffledWindows() else null,
        current = currentMediaItemIndex,
        // After the last song has ended, it comes back from that song's start, not its very end.
        positionMs = if (playbackState == Player.STATE_ENDED) 0 else currentPosition,
    )
}

/** The timeline's windows (the player's songs) in the order they play while shuffled. */
private fun Timeline.shuffledWindows(): List<Int> = buildList {
    var window = getFirstWindowIndex(/* shuffleModeEnabled= */ true)
    while (window != C.INDEX_UNSET) {
        add(window)
        window = getNextWindowIndex(window, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true)
    }
}

/** A saved queue as far as its songs are still on the watch, with the items that play them. */
private class RestoredQueue(val saved: SavedQueue, val items: List<MediaItem>)

/** Loads [queue]: its songs, their order (shuffled or not), and the song it was on and where, paused. */
@OptIn(UnstableApi::class) // setShuffleOrder
private fun ExoPlayer.restore(queue: RestoredQueue) {
    setMediaItems(queue.items, queue.saved.current, queue.saved.positionMs)
    // Setting the songs drew a new shuffle order; the saved one replaces it.
    queue.saved.shuffleOrder?.let { setShuffleOrder(DefaultShuffleOrder(it.toIntArray(), Random.nextLong())) }
    shuffleModeEnabled = queue.saved.shuffleOrder != null
}

/**
 * Lets in Gusle's screens and the system, and turns the song IDs they send into items the player
 * can play. It also looks up the queue the player had last. Callbacks run on the main thread, as
 * the library needs.
 */
private class SongLookup(
    private val library: MusicLibrary,
    private val lastPlayed: LastPlayed,
    private val gusle: String,
    private val scope: CoroutineScope,
) : MediaSession.Callback {
    @OptIn(UnstableApi::class) // isTrusted
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): ConnectionResult =
        // Trusted: the system, e.g. the watch's media controls. Anyone else could find out which
        // songs are on the watch by asking to play IDs, without being allowed to read music.
        if (controller.packageName == gusle || controller.isTrusted) {
            super.onConnect(session, controller)
        } else {
            ConnectionResult.reject()
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
        val items = mediaItems.toPlayable(songs)
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
