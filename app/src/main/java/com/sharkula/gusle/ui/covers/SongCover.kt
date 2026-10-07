package com.sharkula.gusle.ui.covers

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.sharkula.gusle.data.Song
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer

/** A song's cover for Coil: the picture embedded in this version of the song file. */
data class SongCover(val songUri: Uri, val fileModified: Long) {
    constructor(song: Song) : this(song.uri, song.fileModified)
}

/**
 * Reads the picture embedded in a .opus or .m4a file, and lets Coil decode it at the size it
 * needs. Fails when the song has no cover.
 *
 * Not MediaStore's ready-made thumbnail: for a song without a cover, that falls back to any
 * picture in the same folder, e.g. a photo Wear Files saved next to the songs.
 */
class SongCoverFetcher(
    private val cover: SongCover,
    private val options: Options,
    private val pictures: EmbeddedPictures,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val picture = pictures.read(cover, options.context) ?: throw NoCoverException()
        return SourceFetchResult(
            source = ImageSource(Buffer().write(picture), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<SongCover> {
        private val pictures = EmbeddedPictures()

        override fun create(data: SongCover, options: Options, imageLoader: ImageLoader) =
            SongCoverFetcher(data, options, pictures)
    }
}

/**
 * Reads the pictures embedded in song files, which on the watch takes up to a second each. Asked
 * for the same cover while it's being read, e.g. for a row's thumbnail and for the playing song's
 * colors, it waits for that read rather than starting another. Only which songs have no cover is
 * kept afterwards.
 */
class EmbeddedPictures {
    // Coil keeps loaded covers in memory, but not failures; this keeps a missing cover from being
    // looked for again every time its row scrolls into view.
    private val withoutCover = ConcurrentHashMap.newKeySet<SongCover>()
    private val reading = ConcurrentHashMap<SongCover, Deferred<ByteArray?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [cover]'s picture, or null when the song has none. */
    suspend fun read(cover: SongCover, context: Context): ByteArray? {
        if (cover in withoutCover) return null
        // Reading the file can't be stopped halfway, so skip it for a row already scrolled away.
        currentCoroutineContext().ensureActive()
        // Started by the first to wait for it, and goes on when that one stops waiting.
        val read = reading.computeIfAbsent(cover) {
            val app = context.applicationContext
            scope.async(start = CoroutineStart.LAZY) {
                val picture = MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(app, cover.songUri)
                    retriever.embeddedPicture
                }
                if (picture == null) withoutCover += cover
                picture
            }
        }
        read.invokeOnCompletion { reading.remove(cover, read) }
        return read.await()
    }
}

class NoCoverException : Exception("The song has no cover")

/** Keeps each song's cover in Coil's memory cache, apart from anything else loaded for its URI. */
class SongCoverKeyer : Keyer<SongCover> {
    override fun key(data: SongCover, options: Options) = "cover:${data.songUri}@${data.fileModified}"
}
