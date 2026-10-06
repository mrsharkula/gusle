package com.sharkula.gusle.ui.covers

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
    private val withoutCover: MutableSet<SongCover>,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        if (cover in withoutCover) throw NoCoverException()
        // Reading the file can't be stopped halfway, so skip it for a row already scrolled away.
        currentCoroutineContext().ensureActive()
        val picture = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(options.context, cover.songUri)
            retriever.embeddedPicture
        }
        if (picture == null) {
            withoutCover += cover
            throw NoCoverException()
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(picture), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<SongCover> {
        // Coil keeps loaded covers in memory, but not failures; this keeps a missing cover from
        // being looked for again every time its row scrolls into view.
        private val withoutCover = ConcurrentHashMap.newKeySet<SongCover>()

        override fun create(data: SongCover, options: Options, imageLoader: ImageLoader) =
            SongCoverFetcher(data, options, withoutCover)
    }
}

class NoCoverException : Exception("The song has no cover")

/** Keeps each song's cover in Coil's memory cache, apart from anything else loaded for its URI. */
class SongCoverKeyer : Keyer<SongCover> {
    override fun key(data: SongCover, options: Options) = "cover:${data.songUri}@${data.fileModified}"
}
