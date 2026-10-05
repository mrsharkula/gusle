package com.sharkula.gusle.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
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

/** The songs on the watch, as far as Gusle knows them. */
sealed interface Songs {
    /** Not read yet. */
    data object Loading : Songs

    /** Gusle may not read the music on the watch. */
    data object NoAccess : Songs

    /** Every song, A–Z by title. */
    data class Loaded(val list: List<Song>) : Songs
}

/**
 * The songs on the watch, read from MediaStore once and kept in memory. The app keeps one
 * library for the screens and, later, the playback service. [rescan] reads them again.
 */
class MusicLibrary(context: Context) {
    private val context = context.applicationContext
    private val scope = MainScope()
    private var scan: Job? = null

    private val _songs = MutableStateFlow<Songs>(Songs.Loading)
    val songs: StateFlow<Songs> = _songs.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    /** True while the songs are being read. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Reads the songs unless they're already read. */
    fun load() {
        if (songs.value !is Songs.Loaded) rescan()
    }

    /** Reads the songs again, e.g. after music was sent to the watch. Does nothing mid-scan. */
    fun rescan() {
        if (scan?.isActive == true) return
        scan = scope.launch {
            _scanning.value = true
            try {
                _songs.value = withContext(Dispatchers.IO) { readSongs() }
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun readSongs(): Songs {
        // Checked here too, not only by the screens: the playback service can start without them.
        if (context.checkSelfPermission(MusicPermission) != PackageManager.PERMISSION_GRANTED) {
            return Songs.NoAccess
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        // IS_MUSIC leaves out ringtones, notification sounds, alarms and recordings.
        val songs = context.contentResolver.query(
            collection, columns, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val fileName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            buildList(cursor.count) {
                while (cursor.moveToNext()) {
                    val songId = cursor.getLong(id)
                    add(
                        Song(
                            id = songId,
                            uri = ContentUris.withAppendedId(collection, songId),
                            title = cursor.getString(title)?.takeIf { it.isNotBlank() }
                                ?: cursor.getString(fileName).orEmpty().substringBeforeLast('.'),
                            // MediaStore fills in "<unknown>" when there's no artist tag.
                            artist = cursor.getString(artist)
                                ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                            durationMs = cursor.getLong(duration),
                        ),
                    )
                }
            }
        }.orEmpty()
        // The watch's language decides the order, e.g. where "Š" goes.
        return Songs.Loaded(songs.sortedWith(compareBy(Collator.getInstance()) { it.title }))
    }
}
