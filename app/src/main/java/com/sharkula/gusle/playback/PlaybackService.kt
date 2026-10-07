package com.sharkula.gusle.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sharkula.gusle.GusleApp
import com.sharkula.gusle.MainActivity
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.musicLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Plays the songs, also in the background, and shows them in the watch's media controls. The
 * screens connect to it with a MediaController and ask for songs by their [mediaId]s.
 *
 * Swiping Gusle away from the recent apps stops the service unless it's playing (that's what
 * MediaSessionService's own onTaskRemoved does), once nothing is connected to it anymore. Only
 * Gusle's screens and the system connect, and the screens let go when Gusle leaves the screen.
 */
class PlaybackService : MediaSessionService() {
    private val scope = MainScope()
    private var session: MediaSession? = null
    private val playing get() = (application as GusleApp).playing

    /** Tells the screens what's playing, so they can show it without connecting. */
    private val sharePlaying = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = playing.update(player)
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
        player.addListener(sharePlaying)
        playing.update(player)
        session = MediaSession.Builder(this, player)
            // Tapping the watch's media controls opens Gusle, or brings it back if it's open.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setCallback(SongLookup(musicLibrary, packageName, scope))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.removeListener(sharePlaying)
            playing.clear()
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * Lets in Gusle's screens and the system, and turns the song IDs they send into items the player
 * can play. Callbacks run on the main thread, as the library needs.
 */
private class SongLookup(
    private val library: MusicLibrary,
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
     * Runs [block] with the songs on the watch. If they haven't been read yet (the service can
     * start without the screens), reads them first. The returned future fails when they can't be
     * read, or [block] throws; then the player is left as it was.
     */
    private fun <T> withSongs(block: (Map<Long, Song>) -> T): ListenableFuture<T> {
        (library.songs.value as? Songs.Loaded)?.let { loaded ->
            return try {
                Futures.immediateFuture(block(loaded.byId))
            } catch (e: IllegalArgumentException) {
                Futures.immediateFailedFuture(e)
            }
        }
        val result = SettableFuture.create<T>()
        val reading = library.refresh()
        scope.launch {
            // This read's result, not what's there before it ends (e.g. an earlier read that failed).
            reading.join()
            val songs = library.songs.value
            try {
                val loaded = songs as? Songs.Loaded ?: throw IllegalArgumentException("Can't read the songs ($songs)")
                result.set(block(loaded.byId))
            } catch (e: IllegalArgumentException) {
                result.setException(e)
            }
        }
        return result
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
