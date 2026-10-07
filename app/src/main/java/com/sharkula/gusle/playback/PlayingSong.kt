package com.sharkula.gusle.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The song the player has, by its [com.sharkula.gusle.data.Song.id], and whether it's playing. */
data class PlayingSong(val songId: Long, val isPlaying: Boolean)

/**
 * The song in the playback service's player, for the screens that show it without connecting to
 * the service: connecting would start it. The app keeps one; only the service changes it.
 */
class PlayingSongHolder {
    private val playing = MutableStateFlow<PlayingSong?>(null)

    /** Null while the playback service isn't running, or its player has no song. */
    val song: StateFlow<PlayingSong?> = playing.asStateFlow()

    /**
     * Takes the song [player] has now. It counts as playing whenever the play/pause button shows
     * pause, as on Now playing, so it doesn't flicker to paused while the next song buffers.
     */
    @OptIn(UnstableApi::class) // shouldShowPlayButton
    fun update(player: Player) {
        playing.value = player.currentMediaItem?.songId?.let { songId ->
            PlayingSong(songId, isPlaying = !Util.shouldShowPlayButton(player))
        }
    }

    /** No song anymore, e.g. when the service stops. */
    fun clear() {
        playing.value = null
    }
}
