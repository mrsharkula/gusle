package com.sharkula.gusle.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnplayableSongsTest {
    @Test
    fun aMissingUnreadableOrDamagedFileIsTheSongsFault() {
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertTrue(isSongsFault(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
    }

    @Test
    fun whatWouldFailForEverySongIsNot() {
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_TIMEOUT))
        assertFalse(isSongsFault(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED))
    }
}
