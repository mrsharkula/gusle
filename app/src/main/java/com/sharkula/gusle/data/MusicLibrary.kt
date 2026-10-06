package com.sharkula.gusle.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.MainThread
import java.text.CollationKey
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The permission that lets Gusle read the songs on the watch. */
const val MusicPermission = Manifest.permission.READ_MEDIA_AUDIO

fun Context.hasMusicAccess() = checkSelfPermission(MusicPermission) == PackageManager.PERMISSION_GRANTED

/** The songs on the watch, as far as Gusle knows them. */
sealed interface Songs {
    /** Not read yet. */
    data object Loading : Songs

    /** Gusle may not read the music on the watch. */
    data object NoAccess : Songs

    /** Reading failed, and no earlier read has succeeded. */
    data object Failed : Songs

    /** Every song, A–Z by title. */
    data class Loaded(val list: List<Song>) : Songs {
        /** The same songs by [Song.id], built the first time it's needed. */
        val byId: Map<Long, Song> by lazy { list.associateBy { it.id } }
    }
}

/**
 * The songs on the watch, read from MediaStore in one query and kept in memory. The app keeps
 * one library for the screens and the playback service. Use it from the main thread.
 */
class MusicLibrary(context: Context) {
    private val context = context.applicationContext
    private val scope = MainScope()
    private var read: Job? = null
    private var readAgain = false

    private val _songs = MutableStateFlow<Songs>(Songs.Loading)
    val songs: StateFlow<Songs> = _songs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    /** True while the songs are being read. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /**
     * Reads the songs again, e.g. whenever Gusle comes back to the screen. Asked during a read,
     * it reads once more afterwards, so nothing that changed in the meantime is missed. Returns
     * the read, which ends once [songs] holds its result.
     */
    @MainThread
    fun refresh(): Job {
        read?.takeIf { it.isActive }?.let { ongoing ->
            readAgain = true
            return ongoing
        }
        return scope.launch {
            _scanning.value = true
            try {
                do {
                    readAgain = false
                    val songs = withContext(Dispatchers.IO) { readSongs() }
                    when {
                        // An unchanged list equals the current one, so nothing redraws.
                        songs != null -> _songs.value = songs
                        // A failed read keeps the songs from the last good one, if any; the next
                        // refresh tries again.
                        _songs.value !is Songs.Loaded -> _songs.value = Songs.Failed
                    }
                } while (readAgain)
            } finally {
                _scanning.value = false
            }
        }.also { read = it }
    }

    /** Null when MediaStore couldn't be read. */
    private fun readSongs(): Songs? {
        // Checked here too, not only by the screens: the playback service can start without them.
        if (!context.hasMusicAccess()) return Songs.NoAccess
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val songs: List<Song> = try {
            // IS_MUSIC leaves out ringtones, notification sounds, alarms and recordings.
            context.contentResolver.query(
                collection, columns, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val fileName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                buildList<Song>(cursor.count) {
                    while (cursor.moveToNext()) {
                        val songId = cursor.getLong(id)
                        add(
                            Song(
                                id = songId,
                                uri = ContentUris.withAppendedId(collection, songId),
                                // MediaStore uses the file name when there's no title tag, but
                                // not when the tag is blank.
                                title = cursor.getString(title)?.takeIf { it.isNotBlank() }
                                    ?: cursor.getString(fileName).orEmpty().substringBeforeLast('.'),
                                // MediaStore fills in "<unknown>" when there's no artist tag.
                                artist = cursor.getString(artist)
                                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                                durationMs = cursor.getLong(duration),
                                fileModified = cursor.getLong(modified),
                            ),
                        )
                    }
                }
            }
        } catch (e: RuntimeException) {
            Log.w(Tag, "Couldn't read the songs", e)
            null
        } ?: return null // No cursor: MediaStore isn't available right now.

        // The watch's language decides the order, e.g. where "Š" goes. Each title is prepared
        // for comparing once. Equal titles keep a fixed order (artist, then MediaStore's id), so
        // the queue doesn't change between reads.
        val collator = Collator.getInstance()
        val sorted = songs.map { collator.getCollationKey(it.title) to it }
            .sortedWith(
                compareBy<Pair<CollationKey, Song>> { it.first }
                    .thenBy(collator) { it.second.artist.orEmpty() }
                    .thenBy { it.second.id },
            )
            .map { it.second }
        return Songs.Loaded(sorted)
    }

    private companion object {
        const val Tag = "MusicLibrary"
    }
}
