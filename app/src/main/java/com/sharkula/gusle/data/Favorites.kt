package com.sharkula.gusle.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FavoritesPrefs = "favorites"

/**
 * Two lengths this close (ms) are the same song's: MediaStore may work a length out a little
 * differently after an update. Two songs this close, one with the ID the other had, don't matter.
 */
private const val SameLengthMs = 1_000L

/**
 * A song marked as a favorite: when (its order among them, see [Favorites]), and how long the song
 * was then, which tells it from another song MediaStore gives its ID later ([durationMs] null:
 * not known, as an earlier build kept it).
 */
data class Favorite(val addedAtMs: Long, val durationMs: Long?) {
    /** Whether [song], with the favorite's ID, is the song it was marked on. */
    fun isOf(song: Song): Boolean = durationMs == null || abs(song.durationMs - durationMs) <= SameLengthMs

    /** Whether [other], for the same ID, was marked on the same song. */
    internal fun isSameSongAs(other: Favorite): Boolean =
        durationMs == null || other.durationMs == null || abs(durationMs - other.durationMs) <= SameLengthMs
}

/** The favorite songs by [Song.id]. Equal when they hold the same. */
data class FavoriteSongs(private val byId: Map<Long, Favorite>) {
    /** Whether [song] is a favorite: its ID is, and it's the song that was marked ([Favorite.isOf]). */
    operator fun contains(song: Song): Boolean = byId[song.id]?.isOf(song) == true

    /** Whether there are none at all. */
    fun isEmpty(): Boolean = byId.isEmpty()

    /** How many of them are among the songs on the watch, [songsById]. */
    fun countIn(songsById: Map<Long, Song>): Int =
        byId.count { (songId, favorite) -> songsById[songId]?.let(favorite::isOf) == true }

    /**
     * Those among the songs on the watch, [songsById], most recently added first. Two added at the
     * same time keep a fixed order.
     */
    fun newestFirst(songsById: Map<Long, Song>): List<Song> =
        byId.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Favorite>> { it.value.addedAtMs }.thenBy { it.key })
            .mapNotNull { (songId, favorite) -> songsById[songId]?.takeIf(favorite::isOf) }

    internal operator fun get(songId: Long): Favorite? = byId[songId]

    internal val entries: Set<Map.Entry<Long, Favorite>> get() = byId.entries

    /** When the most recently added one was. */
    internal val newestAddedAtMs: Long? get() = byId.values.maxOfOrNull { it.addedAtMs }

    /** These with the song with [songId] as [favorite], or without it when that's null. */
    internal fun with(songId: Long, favorite: Favorite?): FavoriteSongs =
        FavoriteSongs(if (favorite == null) byId - songId else byId + (songId to favorite))

    companion object {
        val None = FavoriteSongs(emptyMap())
    }
}

/**
 * The songs marked as favorites, kept on disk by song ID, each with when it was added and how long
 * it is. The app keeps one, made as a screen or the tile first needs it, and its [songs] are what
 * the screens show: no row reads the disk. Use it from the main thread: it reads the disk once, off
 * it, and writes in the background. [onChange] runs whenever a song is added or removed, e.g. for
 * the tile to follow.
 *
 * Their order is the order they were added in, by the clock, unless it was set back meanwhile: each
 * is added at least a moment after the one before ([clock]).
 *
 * It isn't backed up or moved to another watch (see data_extraction_rules.xml): the song IDs only
 * mean the same songs on this one, and not for ever: MediaStore may give a new song the ID of one
 * deleted. A favorite whose song has another length now is such a song, and goes once the songs are
 * read ([matchWith]). One whose song isn't on the watch stays, out of sight, in case it's back, e.g.
 * once a file copied again is read.
 */
class Favorites internal constructor(
    private val onChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    openPrefs: () -> SharedPreferences,
) {
    constructor(context: Context, onChange: () -> Unit) : this(
        onChange,
        System::currentTimeMillis,
        { context.applicationContext.getSharedPreferences(FavoritesPrefs, Context.MODE_PRIVATE) },
    )

    private val prefs by lazy(openPrefs)

    // Starts right away, so it's usually done long before the songs are read. It also opens the file,
    // so nothing waits for the disk on the main thread later.
    private val reading = MainScope().async(Dispatchers.IO) { read() }
    private var known = false

    /**
     * Songs added (as a favorite) or removed (null) before the disk was read: what was asked, applied
     * over what the disk has, and written, once it's read.
     */
    private val changedBeforeRead = LinkedHashMap<Long, Favorite?>()

    private val favorites = MutableStateFlow(FavoriteSongs.None)

    /** The favorite songs; none until they've been [load]ed, apart from any added meanwhile. */
    val songs: StateFlow<FavoriteSongs> = favorites.asStateFlow()

    /** The favorite songs, read from disk the first time. */
    @MainThread
    suspend fun load(): FavoriteSongs {
        if (!known) {
            val read = reading.await()
            if (!known) {
                favorites.value = applyChangesTo(read)
                known = true
            }
        }
        return favorites.value
    }

    /** Adds [song] to the favorites, or removes it if not [favorite]. */
    @MainThread
    fun set(song: Song, favorite: Boolean) {
        val now = favorites.value
        // Before the read, whether it is one isn't known yet: what's asked is kept for then.
        if (known && (song in now) == favorite) return
        val entry = if (favorite) Favorite(nextAddedAtMs(now), song.durationMs) else null
        if (known) {
            // Written in the background (apply, not commit).
            prefs.edit { write(song.id, entry) }
        } else {
            changedBeforeRead[song.id] = entry
        }
        favorites.value = now.with(song.id, entry)
        onChange()
    }

    /**
     * Matches the favorites with the songs on the watch, [songsById], once they're loaded: a song
     * with a favorite's ID but another length is another song, so the favorite goes; one kept without
     * its length gets it. Nothing to show changes: such songs weren't shown as favorites anyway.
     */
    @MainThread
    fun matchWith(songsById: Map<Long, Song>) {
        if (!known) return
        val now = favorites.value
        var matched = now
        val changes = ArrayList<Pair<Long, Favorite?>>()
        for ((songId, favorite) in now.entries) {
            val song = songsById[songId] ?: continue
            val entry = when {
                !favorite.isOf(song) -> null
                favorite.durationMs == null -> favorite.copy(durationMs = song.durationMs)
                else -> continue
            }
            changes += songId to entry
            matched = matched.with(songId, entry)
        }
        if (changes.isEmpty()) return
        prefs.edit { for ((songId, entry) in changes) write(songId, entry) }
        favorites.value = matched
    }

    /**
     * [read], with the songs added or removed before it was read, as the disk will have them: a song
     * added that was a favorite already keeps when it was first added.
     */
    private fun applyChangesTo(read: FavoriteSongs): FavoriteSongs {
        var loaded = read
        val changes = ArrayList<Pair<Long, Favorite?>>()
        for ((songId, entry) in changedBeforeRead) {
            val existing = read[songId]
            if (entry == null && existing == null) continue
            if (entry != null && existing != null && existing.isSameSongAs(entry)) continue
            val added = entry?.copy(addedAtMs = nextAddedAtMs(loaded))
            changes += songId to added
            loaded = loaded.with(songId, added)
        }
        changedBeforeRead.clear()
        // The file was opened and read off the main thread, so this doesn't wait for the disk.
        if (changes.isNotEmpty()) prefs.edit { for ((songId, entry) in changes) write(songId, entry) }
        return loaded
    }

    /** When a song added now goes: now, unless the clock was set back, then just after the newest. */
    private fun nextAddedAtMs(favorites: FavoriteSongs): Long =
        maxOf(clock(), (favorites.newestAddedAtMs ?: Long.MIN_VALUE) + 1)

    /** One entry per song: its ID, and when it was added and how long it is ("1792431874820,211000"). */
    private fun SharedPreferences.Editor.write(songId: Long, entry: Favorite?) {
        val key = songId.toString()
        if (entry == null) remove(key) else putString(key, "${entry.addedAtMs},${entry.durationMs ?: ""}")
    }

    /**
     * What [write] wrote, and from an earlier build, the time alone (a Long). Anything else, e.g.
     * damaged, is left out.
     */
    private fun read(): FavoriteSongs =
        FavoriteSongs(
            prefs.all.entries.mapNotNull { (key, value) ->
                val songId = key.toLongOrNull() ?: return@mapNotNull null
                val favorite = when (value) {
                    is Long -> Favorite(value, null)
                    is String -> {
                        val addedAtMs = value.substringBefore(',').toLongOrNull() ?: return@mapNotNull null
                        Favorite(addedAtMs, value.substringAfter(',', "").toLongOrNull())
                    }
                    else -> return@mapNotNull null
                }
                songId to favorite
            }.toMap(),
        )
}
