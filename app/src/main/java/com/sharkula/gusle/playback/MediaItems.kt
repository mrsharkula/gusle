package com.sharkula.gusle.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.sharkula.gusle.data.Song

private const val EntryIdKey = "com.sharkula.gusle.PLAYLIST_ENTRY"

/** The ID the player knows a song by. */
val Song.mediaId: String get() = id.toString()

/** The [Song.id] of the song this item plays, or null if it isn't one of Gusle's songs. */
val MediaItem.songId: Long? get() = mediaId.toLongOrNull()

/**
 * The playlist entry this item plays ([com.sharkula.gusle.data.PlaylistEntry.id]), when it's from
 * a playlist; null otherwise. It tells two items of the same song apart, and goes with the item
 * wherever the item goes in the queue.
 */
val MediaItem.entryId: Long?
    get() = requestMetadata.extras?.takeIf { it.containsKey(EntryIdKey) }?.getLong(EntryIdKey)

/**
 * What the screens send the player to play this song: only its ID, and the playlist entry it is,
 * if it's from a playlist ([entryId]). The playback service looks the song up and plays its
 * [toMediaItem] instead, so playable items are built in one place.
 */
fun Song.toMediaItemRequest(entryId: Long? = null): MediaItem =
    MediaItem.Builder().setMediaId(mediaId).withEntry(entryId).build()

/**
 * The item that plays this song: its file, with the title and artist for the media controls, and
 * the playlist entry it is, if it's from a playlist ([entryId]).
 */
fun Song.toMediaItem(entryId: Long? = null): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .withEntry(entryId)
        .setUri(uri)
        .setMediaMetadata(
            // No artwork: the player reads the cover embedded in the file once it plays it.
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .build(),
        )
        .build()

private fun MediaItem.Builder.withEntry(entryId: Long?): MediaItem.Builder {
    if (entryId == null) return this
    val extras = Bundle().apply { putLong(EntryIdKey, entryId) }
    return setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
}
