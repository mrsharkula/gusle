package com.sharkula.gusle.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlin.random.Random

/**
 * The player as the media session, and through it the screens and the watch's media controls, sees
 * it: [player], which skips the songs it can't play and shuffles from the song it's on.
 *
 * A song that fails by its own fault ([isSongsFault]) is marked in [unplayable], and the next one
 * that isn't plays instead, in the order playing; when Previous went to it, the nearest one before
 * it. It plays only if the player was playing: one that fails while paused stays paused. With no
 * such song left, or when it's not the song's fault, e.g. when the audio output fails, it stops
 * there; trying again is a tap on Play. Repeat is never on (the session doesn't offer it), so
 * nothing loops.
 *
 * Turning shuffle on keeps the song it's on first, and the rest follow in a new random order.
 *
 * It knows where its songs came from ([queueSource]), which reaches it with them, and calls their
 * queue that ([nameOf]) in its playlist title, for the Queue and the watch's media controls.
 *
 * Every command reaches the player through here, so it knows when Previous went to a song. Its own
 * skips go to [player] directly and keep that direction.
 */
@OptIn(UnstableApi::class)
class SessionPlayer(
    private val player: ExoPlayer,
    private val unplayable: UnplayableSongs,
    private val nameOf: (QueueSource) -> String,
    private val random: Random = Random,
) : ForwardingPlayer(player) {
    /** Whether Previous went to the song it's on, until that song plays or another command moves on. */
    private var wentBack = false

    /**
     * Where its songs came from: set together with them, as their first item notes it
     * ([comingFrom]), so the two never disagree, also as they're saved.
     */
    var queueSource: QueueSource = QueueSource.AllSongs
        private set

    private val skipUnplayable = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                wentBack = false
                // E.g. tapped again, or its file is back.
                player.currentMediaItem?.songId?.let(unplayable::plays)
            }
        }

        override fun onPlayerError(error: PlaybackException) = songFailed(error.errorCode)
    }

    init {
        player.addListener(skipUnplayable)
    }

    override fun seekToPrevious() = goBack { super.seekToPrevious() }

    override fun seekToPreviousMediaItem() = goBack { super.seekToPreviousMediaItem() }

    // Every other way the session goes to another song.
    override fun seekToNext() = moveOn { super.seekToNext() }

    override fun seekToNextMediaItem() = moveOn { super.seekToNextMediaItem() }

    override fun seekToDefaultPosition(mediaItemIndex: Int) = moveOn { super.seekToDefaultPosition(mediaItemIndex) }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) = moveOn { super.seekTo(mediaItemIndex, positionMs) }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) = moveOn {
        queueSource = QueueSource.of(mediaItems)
        super.setMediaItems(mediaItems, resetPosition)
        nameQueue()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) = moveOn {
        queueSource = QueueSource.of(mediaItems)
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        nameQueue()
    }

    override fun clearMediaItems() {
        queueSource = QueueSource.AllSongs
        super.clearMediaItems()
        nameQueue()
    }

    /**
     * Comes back to a queue saved earlier, paused and not prepared: its songs ([items], which came
     * from [source]), the one at [current] at [positionMs], and if it was shuffled, the order they
     * played in ([shuffleOrder]).
     */
    fun restore(
        items: List<MediaItem>,
        source: QueueSource,
        current: Int,
        positionMs: Long,
        shuffleOrder: List<Int>?,
    ) {
        queueSource = source
        player.setMediaItems(items, current, positionMs)
        nameQueue()
        // Setting the songs drew a new shuffle order; the saved one replaces it.
        shuffleOrder?.let { player.setShuffleOrder(DefaultShuffleOrder(it.toIntArray(), random.nextLong())) }
        player.shuffleModeEnabled = shuffleOrder != null
    }

    /**
     * Gives its queue the name of where its songs came from, or none while it has no songs. Call it
     * again when the name may have changed, e.g. once the songs are read anew.
     */
    fun nameQueue() {
        player.playlistMetadata = if (player.mediaItemCount == 0) {
            MediaMetadata.EMPTY
        } else {
            MediaMetadata.Builder().setTitle(nameOf(queueSource)).build()
        }
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Shuffle all turns it on before setting its songs, which draws an order of their own.
        if (shuffleModeEnabled && !player.shuffleModeEnabled && player.mediaItemCount > 0) {
            val current = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledFrom(current, player.mediaItemCount, random), random.nextLong()))
        }
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun release() {
        // Releasing can report an error too; there's nothing to skip to then.
        player.removeListener(skipUnplayable)
        super.release()
    }

    private inline fun goBack(seek: () -> Unit) {
        val from = player.currentMediaItemIndex
        seek()
        // A few seconds into a song, Previous starts it again: that's not going back.
        wentBack = player.currentMediaItemIndex != from
    }

    private inline fun moveOn(command: () -> Unit) {
        wentBack = false
        command()
    }

    /** The song it's on failed with a PlaybackException's [errorCode]. */
    internal fun songFailed(errorCode: Int) {
        // The player reports a song's errors only once it's on that song.
        val item = player.currentMediaItem
        val songId = item?.songId?.takeIf { isSongsFault(errorCode) }
        unplayable.failed(songId, item?.mediaMetadata?.title?.toString())
        val next = if (songId == null) {
            null
        } else {
            val marked = unplayable.songIds.value
            nextPlayable(
                player.currentTimeline.playOrder(player.shuffleModeEnabled),
                from = player.currentMediaItemIndex,
                backwards = wentBack,
            ) { player.getMediaItemAt(it).songId !in marked }
        }
        if (next == null) {
            player.pause()
        } else {
            player.seekToDefaultPosition(next)
            player.prepare()
        }
    }
}
