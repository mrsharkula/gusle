package com.sharkula.gusle.playback

import androidx.annotation.OptIn
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
 * The song in the playback service's player, for the screens that show it without connecting to
 * the service: connecting would start it. Without one, it's the song the player had last
 * ([LastPlayed]), paused: the service comes back to it when it starts. The app keeps one; only the
 * service changes it, apart from Stop playing forgetting a saved song ([forgetIfOnlySaved]).
 */
class PlayingSongHolder(private val library: MusicLibrary, private val lastPlayed: LastPlayed) {
    private val playing = MutableStateFlow<PlayingSong?>(null)

    /** The player's song while the service is running and its player has one. */
    private var fromPlayer: PlayingSong? = null

    /** Null while there's none, e.g. before anything has played. */
    val song: StateFlow<PlayingSong?> = playing.asStateFlow()

    init {
        MainScope().launch {
            // Read off the main thread. The screens wait for the songs to be read anyway, which
            // takes longer.
            lastPlayed.load()
            // If the song saved last is deleted, the service comes back to the next one left.
            library.songs.collect { show() }
        }
    }

    /**
     * Takes the song [player] has now. It counts as playing whenever the play/pause button shows
     * pause, as on Now playing, so it doesn't flicker to paused while the next song buffers.
     */
    @OptIn(UnstableApi::class) // shouldShowPlayButton
    fun update(player: Player) {
        fromPlayer = player.currentMediaItem?.songId?.let { songId ->
            PlayingSong(songId, isPlaying = !Util.shouldShowPlayButton(player))
        }
        show()
    }

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

    /** The service stopped; it has saved its song. */
    fun serviceStopped() {
        fromPlayer = null
        show()
    }

    private fun show() {
        playing.value = fromPlayer ?: lastPlayed.saved?.let { saved ->
            val songs = (library.songs.value as? Songs.Loaded)?.byId
            val left = if (songs == null) saved else saved.keepOnly(songs::containsKey)
            left?.let { PlayingSong(it.currentSongId, isPlaying = false) }
        }
    }
}
