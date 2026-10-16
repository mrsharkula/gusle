package com.sharkula.gusle.tile

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async

private const val TilePrefs = "tile"
private const val TileIdsKey = "tile_ids"
private const val StillKey = "shown_still"
private const val CountsFromKey = "shown_counts_from_ms"

/**
 * What Gusle knows of its tile, kept on disk, as it has to hold after Gusle's process ends: which
 * of its tiles the watch has (by the IDs the system gives them; one, usually), and how the tile
 * looked last ([TileLook]). Use it from the main thread: it reads the disk once, off it, and writes
 * in the background. It isn't backed up or moved to another watch (see data_extraction_rules.xml):
 * it's about this watch's tiles.
 */
class TileRecord internal constructor(openPrefs: () -> SharedPreferences) {
    constructor(context: Context) : this(
        { context.applicationContext.getSharedPreferences(TilePrefs, Context.MODE_PRIVATE) },
    )

    private val prefs by lazy(openPrefs)

    // Starts right away, so it's usually done by the time anything asks.
    private val reading = MainScope().async(Dispatchers.IO) { read() }

    /**
     * The IDs of Gusle's tiles on the watch, or null when they've never been recorded, e.g. after
     * an update from a Gusle without the record.
     */
    @MainThread
    suspend fun tileIds(): Set<Int>? = reading.await().tileIds

    /** How the tile looked last, or null when that's not known. */
    @MainThread
    suspend fun look(): TileLook? = reading.await().look

    /** The watch has a tile of Gusle's with [tileId], e.g. one just added. */
    @MainThread
    suspend fun added(tileId: Int) {
        val known = reading.await()
        val ids = known.tileIds
        if (ids == null || tileId !in ids) known.write(ids.orEmpty() + tileId)
    }

    /** The tile with [tileId] has been removed from the watch. */
    @MainThread
    suspend fun removed(tileId: Int) {
        val known = reading.await()
        val ids = known.tileIds
        if (ids == null || tileId in ids) known.write(ids.orEmpty() - tileId)
    }

    /** The watch has these tiles, as the system says, unless they've been recorded meanwhile. */
    @MainThread
    suspend fun foundTiles(tileIds: Set<Int>) {
        val known = reading.await()
        if (known.tileIds == null) known.write(tileIds)
    }

    /** The tile looks like [look] now. */
    @MainThread
    suspend fun shown(look: TileLook) {
        val known = reading.await()
        if (look == known.look) return
        known.look = look
        // Written in the background (apply, not commit).
        prefs.edit {
            putString(StillKey, look.still)
            if (look.countsFromMs == null) remove(CountsFromKey) else putLong(CountsFromKey, look.countsFromMs)
        }
    }

    private fun Known.write(ids: Set<Int>) {
        tileIds = ids
        prefs.edit { putString(TileIdsKey, ids.sorted().joinToString(",")) }
    }

    private fun read(): Known =
        try {
            val ids = prefs.getString(TileIdsKey, null)?.let { stored ->
                if (stored.isEmpty()) emptySet() else stored.split(',').mapNotNull(String::toIntOrNull).toSet()
            }
            val look = prefs.getString(StillKey, null)?.let { still ->
                TileLook(still, if (prefs.contains(CountsFromKey)) prefs.getLong(CountsFromKey, 0) else null)
            }
            Known(ids, look)
        } catch (e: ClassCastException) {
            // A value of another type, e.g. from an earlier build: unknown, rather than a crash on
            // every start.
            Known(null, null)
        }

    /** The record in memory, once it's read; only changed on the main thread. */
    private class Known(var tileIds: Set<Int>?, var look: TileLook?)
}
