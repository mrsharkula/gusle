package com.sharkula.gusle.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PlaylistsPrefs = "playlists"

/** The ID the next playlist gets: past every playlist made so far, deleted ones too. */
private const val NextIdKey = "next_id"

/**
 * A song in a playlist: the entry's own [id], which no other entry in any playlist ever has, and
 * which stays while entries around it come and go; the song's ID; and how long the song was when it
 * was added, which tells it from another song MediaStore gives its ID later (as a favorite's
 * [Favorite.isOf] does).
 */
data class PlaylistEntry(val id: Long, val songId: Long, val durationMs: Long) {
    /** Whether [song], with the entry's song ID, is the song that was added. */
    fun isOf(song: Song): Boolean = isSameLength(song.durationMs, durationMs)
}

/** A song a playlist shows: the [song], and which of the playlist's entries it is ([PlaylistEntry.id]). */
data class PlaylistSong(val entryId: Long, val song: Song)

/**
 * A playlist: its [id], which no other playlist ever has, not even after this one is deleted; its
 * [name]; and its [entries] in the order they were added. A song is there as often as it was added.
 */
data class Playlist(val id: Long, val name: String, val entries: List<PlaylistEntry>) {
    /** Its songs that are on the watch, [songsById], in its order, each with its entry. */
    fun songsIn(songsById: Map<Long, Song>): List<PlaylistSong> =
        entries.mapNotNull { entry -> songOf(entry, songsById)?.let { PlaylistSong(entry.id, it) } }

    /** How many of its songs are on the watch, [songsById]. */
    fun countIn(songsById: Map<Long, Song>): Int = entries.count { songOf(it, songsById) != null }

    /** Its first song that's on the watch, [songsById], e.g. for its cover; null when there's none. */
    fun firstSongIn(songsById: Map<Long, Song>): Song? = entries.firstNotNullOfOrNull { songOf(it, songsById) }

    private fun songOf(entry: PlaylistEntry, songsById: Map<Long, Song>): Song? =
        songsById[entry.songId]?.takeIf(entry::isOf)
}

/**
 * The playlists, kept on disk. The app keeps one, and its [all] are what the screens and the queue's
 * name show: no row reads the disk. Use it from the main thread: it reads the disk once, off it, and
 * writes in the background.
 *
 * Kept like the favorites ([Favorites]): on this watch only, not backed up or moved to another one
 * (see data_extraction_rules.xml), since the song IDs only mean the same songs here. An entry whose
 * song has another length now is another song with a deleted one's ID, and goes once the songs are
 * read ([matchWith]); one whose song isn't on the watch stays, out of sight, in case it's back.
 */
class Playlists internal constructor(openPrefs: () -> SharedPreferences) {
    constructor(context: Context) : this(
        { context.applicationContext.getSharedPreferences(PlaylistsPrefs, Context.MODE_PRIVATE) },
    )

    private val prefs by lazy(openPrefs)

    // Starts right away, so it's done long before anything shows the playlists. It also opens the
    // file, so nothing waits for the disk on the main thread later.
    private val reading = MainScope().async(Dispatchers.IO) { read() }
    private var nextId = 1L

    /** Songs deleted from the watch before the disk was read: they leave the playlists once it is. */
    private val forgottenBeforeRead = HashSet<Long>()

    private val playlists = MutableStateFlow<List<Playlist>?>(null)

    /** The playlists, oldest first; null until they've been read ([load]). */
    val all: StateFlow<List<Playlist>?> = playlists.asStateFlow()

    /** The playlists, read from disk the first time. */
    @MainThread
    suspend fun load(): List<Playlist> {
        playlists.value?.let { return it }
        val read = reading.await()
        // Unless another load was first.
        playlists.value?.let { return it }
        nextId = read.nextId
        playlists.value = read.playlists
        if (forgottenBeforeRead.isNotEmpty()) {
            val forgotten = forgottenBeforeRead.toSet()
            forgottenBeforeRead.clear()
            change { playlist -> playlist.copy(entries = playlist.entries.filter { it.songId !in forgotten }) }
        }
        return playlists.value.orEmpty()
    }

    /** Makes a new, empty playlist called [name], after the others; returns its ID. */
    @MainThread
    suspend fun create(name: String): Long {
        val before = load()
        val playlist = Playlist(nextId++, name, emptyList())
        // Written in the background (apply, not commit).
        prefs.edit {
            putLong(NextIdKey, nextId)
            write(playlist)
        }
        playlists.value = before + playlist
        return playlist.id
    }

    /**
     * The song with [songId] was deleted from the watch: it leaves every playlist, whatever song it
     * was added as, since MediaStore may give its ID to a file added later.
     */
    @MainThread
    fun forget(songId: Long) {
        if (playlists.value == null) {
            forgottenBeforeRead += songId
            return
        }
        change { playlist -> playlist.copy(entries = playlist.entries.filter { it.songId != songId }) }
    }

    /**
     * Matches the playlists with the songs on the watch, [songsById], once both are loaded: an entry
     * whose song has its ID but another length is of another song, so it goes. Nothing shown changes:
     * such entries weren't shown anyway.
     */
    @MainThread
    fun matchWith(songsById: Map<Long, Song>) {
        change { playlist ->
            playlist.copy(entries = playlist.entries.filter { entry -> songsById[entry.songId]?.let(entry::isOf) != false })
        }
    }

    /** Has every playlist [changed], and writes those that changed. */
    private fun change(changed: (Playlist) -> Playlist) {
        val before = playlists.value ?: return
        val writes = ArrayList<Playlist>()
        val after = before.map { playlist -> changed(playlist).also { if (it != playlist) writes += it } }
        if (writes.isEmpty()) return
        prefs.edit { writes.forEach { write(it) } }
        playlists.value = after
    }

    /**
     * One entry per playlist, by its ID: its entries, each its own ID, the song's ID and length, then
     * a line break and its name ("1,1792,211000,2,1795,183500\nRoad trip"). A name may hold anything,
     * a line break too.
     */
    private fun SharedPreferences.Editor.write(playlist: Playlist) {
        val entries = playlist.entries.joinToString(",") { "${it.id},${it.songId},${it.durationMs}" }
        putString(playlist.id.toString(), entries + "\n" + playlist.name)
    }

    private class Read(val playlists: List<Playlist>, val nextId: Long)

    /**
     * What [write] wrote. Anything else, e.g. damaged, is left out: a playlist, or just an entry of
     * it, and an entry whose ID an older playlist, or one before it, has already.
     */
    private fun read(): Read {
        val all = prefs.all
        val seen = HashSet<Long>()
        val playlists = all.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            if ('\n' !in text) return@mapNotNull null
            Playlist(id, text.substringAfter('\n'), readEntries(text.substringBefore('\n')))
        }.sortedBy { it.id }.map { playlist -> playlist.copy(entries = playlist.entries.filter { seen.add(it.id) }) }
        val pastLast = (playlists.maxOfOrNull { it.id } ?: 0) + 1
        return Read(playlists, maxOf(all[NextIdKey] as? Long ?: 1, pastLast))
    }

    private fun readEntries(entries: String): List<PlaylistEntry> {
        if (entries.isEmpty()) return emptyList()
        val numbers = entries.split(',').map { it.toLongOrNull() }
        return (0 until numbers.size / 3).mapNotNull { i ->
            val id = numbers[3 * i] ?: return@mapNotNull null
            val songId = numbers[3 * i + 1] ?: return@mapNotNull null
            val durationMs = numbers[3 * i + 2] ?: return@mapNotNull null
            PlaylistEntry(id, songId, durationMs)
        }
    }
}
