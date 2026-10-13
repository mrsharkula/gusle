package com.sharkula.gusle.playback

import android.content.res.Resources
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.data.albumNameIn

/**
 * Where a queue's songs came from, as it's kept with them: what it is, not what it's called, so the
 * name can be looked up anew ([name]), e.g. once an artist is spelled another way.
 */
sealed interface QueueSource {
    /** Every song; also a queue saved before Gusle kept where its songs came from. */
    data object AllSongs : QueueSource

    /** An artist's songs, by [com.sharkula.gusle.data.Artist.key]; null for the songs without an artist. */
    data class Artist(val key: String?) : QueueSource

    /** An album's songs, by [com.sharkula.gusle.data.Album.key]; null for the songs without an album. */
    data class Album(val key: String?) : QueueSource

    /** As it's saved and sent; null for [AllSongs]. */
    val encoded: String?
        get() = when (this) {
            AllSongs -> null
            // No artist's or album's key is empty, so the songs without one can have that.
            is Artist -> ArtistPrefix + key.orEmpty()
            is Album -> AlbumPrefix + key.orEmpty()
        }

    companion object {
        /** The source saved or sent as [encoded]; anything else, e.g. from an earlier build, is All songs. */
        fun decode(encoded: String?): QueueSource = when {
            encoded == null -> AllSongs
            encoded.startsWith(ArtistPrefix) -> Artist(encoded.removePrefix(ArtistPrefix).ifEmpty { null })
            encoded.startsWith(AlbumPrefix) -> Album(encoded.removePrefix(AlbumPrefix).ifEmpty { null })
            else -> AllSongs
        }

        /** Where the songs [items] came from, as [comingFrom] noted it; [AllSongs] if it didn't. */
        fun of(items: List<MediaItem>): QueueSource =
            decode(items.firstOrNull()?.requestMetadata?.extras?.getString(SourceKey))
    }
}

private const val ArtistPrefix = "artist:"
private const val AlbumPrefix = "album:"
private const val SourceKey = "com.sharkula.gusle.QUEUE_SOURCE"

/**
 * These items, the first one noting that they came from [source] ([QueueSource.of]), so it reaches
 * the player together with them: from the screens to the playback service, and on into its player.
 */
fun List<MediaItem>.comingFrom(source: QueueSource): List<MediaItem> {
    val first = firstOrNull() ?: return this
    val extras = Bundle().apply { putString(SourceKey, source.encoded) }
    val noted = first.buildUpon()
        .setRequestMetadata(first.requestMetadata.buildUpon().setExtras(extras).build())
        .build()
    return listOf(noted) + drop(1)
}

/**
 * What the player's queue is called, after where its songs came from ([SessionPlayer.nameQueue]);
 * null while it has no songs, and from a player let go of.
 */
val Player.queueName: String? get() = playlistMetadata.title?.toString()

/**
 * What the Queue and the watch's media controls call this source: "All songs", the artist's or the
 * album's name as the [songs] spell it now, or "Unknown artist" or "Unknown album". An artist or
 * album no longer among them, e.g. after their songs were tagged anew, goes by the name in its key.
 */
fun QueueSource.name(songs: Songs.Loaded?, resources: Resources): String = when (this) {
    QueueSource.AllSongs -> resources.getString(R.string.all_songs)
    is QueueSource.Artist -> when (key) {
        null -> resources.getString(R.string.unknown_artist)
        else -> songs?.artistsByKey?.get(key)?.name ?: key
    }
    is QueueSource.Album -> when (key) {
        null -> resources.getString(R.string.unknown_album)
        else -> songs?.albumsByKey?.get(key)?.name ?: albumNameIn(key)
    }
}
