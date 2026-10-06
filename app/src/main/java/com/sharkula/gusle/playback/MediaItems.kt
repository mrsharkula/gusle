package com.sharkula.gusle.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.sharkula.gusle.data.Song

/** The ID the player knows a song by. */
val Song.mediaId: String get() = id.toString()

/**
 * What the screens send the player to play this song: only its ID. The playback service looks
 * the song up and plays its [toMediaItem] instead, so playable items are built in one place.
 */
fun Song.toMediaItemRequest(): MediaItem = MediaItem.Builder().setMediaId(mediaId).build()

/** The item that plays this song: its file, with the title and artist for the media controls. */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            // No artwork: the player reads the cover embedded in the file once it plays it.
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build(),
        )
        .build()
