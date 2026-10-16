package com.sharkula.gusle.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Songs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The song the player has, by its [com.sharkula.gusle.data.Song.id], and whether it's playing. */
data class PlayingSong(val songId: Long, val isPlaying: Boolean)

/**
 * Whether this player counts as playing: whenever its play/pause button shows pause, as on Now
 * playing, so it doesn't flicker to paused while the next song buffers.
 */
@OptIn(UnstableApi::class) // shouldShowPlayButton
val Player.playsOn: Boolean get() = !Util.shouldShowPlayButton(this)

/** How long this player's song is, in ms, once it knows; null until then. */
val Player.knownDurationMs: Long? get() = duration.takeIf { it != C.TIME_UNSET }

/**
 * The song in the playback service's player, for the screens that show it without connecting to
 * the service: connecting would start it. Without one, it's the song the player had last
 * ([LastPlayed]), paused: the service comes back to it when it starts. The app keeps one; only the
 * service changes it, apart from Stop playing forgetting a saved song ([forgetIfOnlySaved]).
 * [onChange] runs whenever the song or whether it plays changes, though not as the saved one is
 * first read: the tile follows it.
 */
class PlayingSongHolder(
    private val library: MusicLibrary,
    private val lastPlayed: LastPlayed,
    private val onChange: () -> Unit = {},
) {
    private val playing = MutableStateFlow<PlayingSong?>(null)

    /** The player's song while the service is running and its player has one. */
    private var fromPlayer: PlayingSong? = null

    /** The service's player while [fromPlayer] comes from it, for where it is in its song. */
    private var player: Player? = null

    /** The saved queue as far as its songs are still on the watch, while the song comes from it. */
    private var savedLeft: SavedQueue? = null

    /** Whether a song (or none) has been shown since the saved one was read, so a new one is a change. */
    private var shown = false

    /** Null while there's none, e.g. before anything has played. */
    val song: StateFlow<PlayingSong?> = playing.asStateFlow()

    private val foreground = MutableStateFlow(false)

    /**
     * Whether the playback service is in the foreground: while it plays, and for a while after a
     * pause. Then it goes on when nothing is connected to it anymore, e.g. once the tile that
     * started a song lets go of it.
     */
    val inForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        MainScope().launch {
            // Read off the main thread. The screens wait for the songs to be read anyway, which
            // takes longer.
            lastPlayed.load()
            // If the song saved last is deleted, the service comes back to the next one left.
            library.songs.collect { show() }
        }
    }

    /** Takes the song [player] has now, and whether it plays on ([playsOn]). */
    fun update(player: Player) {
        fromPlayer = player.currentMediaItem?.songId?.let { songId -> PlayingSong(songId, isPlaying = player.playsOn) }
        this.player = player.takeIf { fromPlayer != null }
        show()
    }

    /**
     * The song, once the saved one has been read, for the tile, which may start the app on its own.
     * Read the songs first: until then, a saved song that's gone from the watch still counts.
     */
    suspend fun load(): PlayingSong? {
        lastPlayed.load()
        show()
        return playing.value
    }

    /** Where in the song it is, in ms: the player's position while the service has it, or the saved one. */
    fun positionMs(): Long = player?.currentPosition ?: savedLeft?.positionMs ?: 0

    /** How long the song is, in ms, if the service's player knows it already; otherwise null. */
    fun durationMs(): Long? = player?.knownDurationMs

    /**
     * Forgets the song saved to come back to, for Stop playing, when the service's player has no
     * song: then there's no player to stop, and the service isn't started just to empty it. A
     * service starting meanwhile finds nothing to come back to. False, forgetting nothing, when the
     * player has a song: the service has to stop it.
     */
    fun forgetIfOnlySaved(): Boolean {
        if (fromPlayer != null) return false
        lastPlayed.forget()
        show()
        return true
    }

    /** The service went into the foreground, or left it. */
    fun inForeground(inForeground: Boolean) {
        foreground.value = inForeground
    }

    /** The service stopped; it has saved its song. */
    fun serviceStopped() {
        fromPlayer = null
        player = null
        foreground.value = false
        show()
    }

    private fun show() {
        savedLeft = if (fromPlayer != null) {
            null
        } else {
            lastPlayed.saved?.let { saved ->
                val songs = (library.songs.value as? Songs.Loaded)?.byId
                if (songs == null) saved else saved.keepOnly(songs::containsKey)
            }
        }
        val song = fromPlayer ?: savedLeft?.let { PlayingSong(it.currentSongId, isPlaying = false) }
        val changed = song != playing.value
        playing.value = song
        if (changed && shown) onChange()
        shown = true
    }
}
