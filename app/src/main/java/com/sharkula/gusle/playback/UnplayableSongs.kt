package com.sharkula.gusle.playback

import android.os.SystemClock
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether a playback error with [errorCode] (a [PlaybackException]'s) is the song's own fault: its
 * file is gone, can't be read or ends too soon, or it can't be parsed or decoded. Anything else,
 * e.g. the audio output failing, a decoder taken away by another app, or an unexpected error, would
 * likely fail for every song. A file cut short fails with the unspecified IO error (an
 * EOFException), so that one counts too.
 */
fun isSongsFault(errorCode: Int): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    -> true
    else -> false
}

/**
 * The songs the player couldn't play, by [com.sharkula.gusle.data.Song.id], for as long as the
 * playback service runs, and the last time one failed. The service marks and skips them; the
 * screens can show them without connecting, like [PlayingSongHolder]'s song. The app keeps one;
 * only the service changes it. Use it from the main thread.
 */
class UnplayableSongs(private val now: () -> Long = SystemClock::elapsedRealtime) {
    /** A song failed at [atMs] ([SystemClock.elapsedRealtime]); [title] is the song's if it was its fault. */
    data class Failure(val songsFault: Boolean, val title: String?, val atMs: Long)

    private val marked = MutableStateFlow<Set<Long>>(emptySet())
    private val lastFailed = MutableStateFlow<Failure?>(null)

    val songIds: StateFlow<Set<Long>> = marked.asStateFlow()

    /** The last song that failed to play since the service started, whether it was its fault or not. */
    val lastFailure: StateFlow<Failure?> = lastFailed.asStateFlow()

    /**
     * A song failed just now: [songId], titled [title], if it was its own fault, which marks it; null
     * if it wasn't.
     */
    fun failed(songId: Long?, title: String?) {
        if (songId != null) marked.update { it + songId }
        lastFailed.value = Failure(songsFault = songId != null, title = title?.takeIf { songId != null }, atMs = now())
    }

    /** [songId] plays after all, e.g. when it's tried again: it's no longer marked. */
    fun plays(songId: Long) {
        marked.update { it - songId }
    }

    /** The service stopped; the marks go with it. */
    fun serviceStopped() {
        marked.value = emptySet()
        lastFailed.value = null
    }
}
