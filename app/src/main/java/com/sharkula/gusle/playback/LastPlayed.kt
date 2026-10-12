package com.sharkula.gusle.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async

/**
 * A queue as the player had it: its songs by [com.sharkula.gusle.data.Song.id] in the player's
 * order (e.g. A–Z), the order they play in while shuffled, where it was, and where its songs came
 * from.
 */
data class SavedQueue(
    val songIds: List<Long>,
    /** Indices into [songIds] in the order they play while shuffle is on; null while it's off. */
    val shuffleOrder: List<Int>?,
    /** The index in [songIds] of the song it was on. */
    val current: Int,
    val positionMs: Long,
    /** Where the songs came from; All songs in a queue saved before Gusle kept it. */
    val source: QueueSource = QueueSource.AllSongs,
) {
    val currentSongId: Long get() = songIds[current]

    /** Whether [other] has the same songs, orders and source: all but where it is. */
    fun sameSongsAs(other: SavedQueue): Boolean =
        songIds == other.songIds && shuffleOrder == other.shuffleOrder && source == other.source

    /**
     * This queue with only the songs [isThere] (still on the watch), in the same orders, or null if
     * none of them is. If the song it was on is gone, it's on the next one left in play order, from
     * its start, or else on the last one left.
     */
    fun keepOnly(isThere: (songId: Long) -> Boolean): SavedQueue? {
        // Where each song that's left goes in the new queue; -1 for those that are gone.
        var left = 0
        val newIndex = IntArray(songIds.size) { if (isThere(songIds[it])) left++ else -1 }
        if (left == songIds.size) return this
        if (left == 0) return null
        val playOrder = shuffleOrder ?: songIds.indices.toList()
        val playOrderLeft = playOrder.filter { newIndex[it] >= 0 }
        val stays = newIndex[current] >= 0
        val newCurrent = if (stays) {
            newIndex[current]
        } else {
            val next = nextPlayable(playOrder, current, backwards = false) { newIndex[it] >= 0 }
            newIndex[next ?: playOrderLeft.last()]
        }
        return SavedQueue(
            songIds = songIds.filterIndexed { i, _ -> newIndex[i] >= 0 },
            shuffleOrder = shuffleOrder?.let { playOrderLeft.map { newIndex[it] } },
            current = newCurrent,
            positionMs = if (stays) positionMs else 0,
            source = source,
        )
    }

    companion object {
        /**
         * The queue stored as [songIds] and [shuffleOrder] (numbers joined by [joinedNumbers]), on
         * [currentSongId] at [positionMs], from [source], or null if it doesn't hold together, e.g.
         * when a file is damaged. A song that isn't in the queue (the two were stored separately)
         * starts it over.
         */
        fun of(
            songIds: String?,
            shuffleOrder: String?,
            currentSongId: Long,
            positionMs: Long,
            source: QueueSource = QueueSource.AllSongs,
        ): SavedQueue? {
            val ids = songIds?.splitNumbers(String::toLongOrNull) ?: return null
            val order = shuffleOrder?.let { it.splitNumbers(String::toIntOrNull) ?: return null }
            if (ids.isEmpty() || positionMs < 0 || (order != null && order.sorted() != ids.indices.toList())) {
                return null
            }
            val current = ids.indexOf(currentSongId)
            return if (current >= 0) {
                SavedQueue(ids, order, current, positionMs, source)
            } else {
                SavedQueue(ids, order, current = order?.first() ?: 0, positionMs = 0, source)
            }
        }
    }
}

/** Numbers as they're stored: "12,7,30". */
fun List<Number>.joinedNumbers(): String = joinToString(",")

private fun <T : Any> String.splitNumbers(toNumber: (String) -> T?): List<T>? =
    if (isEmpty()) emptyList() else split(',').map { toNumber(it) ?: return null }

// Two files: while playing, only the small one with the position is written every few seconds.
private const val QueuePrefs = "last_played_queue"
private const val PositionPrefs = "last_played_position"
private const val SongIdsKey = "song_ids"
private const val ShuffleOrderKey = "shuffle_order"
private const val SourceKey = "source"
private const val SongIdKey = "song_id"
private const val PositionKey = "position_ms"

/**
 * The queue the player had last, kept on disk so Gusle can come back to it after the playback
 * service stops, the process dies or the watch restarts. The app keeps one, for the service and the
 * screens. Use it from the main thread: it reads the disk once, off it, and writes in the
 * background. It isn't backed up or moved to another watch (see data_extraction_rules.xml): the
 * song IDs only mean the same songs on this one.
 */
class LastPlayed internal constructor(openPrefs: (name: String) -> SharedPreferences) {
    constructor(context: Context) : this(
        { name -> context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) },
    )

    private val queuePrefs by lazy { openPrefs(QueuePrefs) }
    private val positionPrefs by lazy { openPrefs(PositionPrefs) }

    // Starts right away, so it's usually done by the time anything asks.
    private val reading = MainScope().async(Dispatchers.IO) { read() }
    private var known = false

    /** The queue saved last, once it's been [load]ed or saved; null if there's none. */
    var saved: SavedQueue? = null
        private set

    /** The queue saved last, or null if there's none; read from disk the first time. */
    @MainThread
    suspend fun load(): SavedQueue? {
        if (!known) {
            val read = reading.await()
            // Unless something was saved meanwhile: that's newer.
            if (!known) {
                saved = read
                known = true
            }
        }
        return saved
    }

    /**
     * Keeps [queue], writing only what changed: its songs, their order and where they came from, or
     * where it is.
     */
    @MainThread
    fun save(queue: SavedQueue) {
        val last = saved
        if (queue == last) return
        // Written in the background (apply, not commit).
        if (last == null || !queue.sameSongsAs(last)) {
            queuePrefs.edit {
                putString(SongIdsKey, queue.songIds.joinedNumbers())
                // Null removes it.
                putString(ShuffleOrderKey, queue.shuffleOrder?.joinedNumbers())
                // Null, for All songs, removes it.
                putString(SourceKey, queue.source.encoded)
            }
        }
        if (last == null || queue.currentSongId != last.currentSongId || queue.positionMs != last.positionMs) {
            positionPrefs.edit {
                putLong(SongIdKey, queue.currentSongId)
                putLong(PositionKey, queue.positionMs)
            }
        }
        saved = queue
        known = true
    }

    /** Forgets the queue, e.g. once none of its songs is on the watch anymore. */
    @MainThread
    fun forget() {
        queuePrefs.edit { clear() }
        positionPrefs.edit { clear() }
        saved = null
        known = true
    }

    private fun read(): SavedQueue? =
        try {
            SavedQueue.of(
                songIds = queuePrefs.getString(SongIdsKey, null),
                shuffleOrder = queuePrefs.getString(ShuffleOrderKey, null),
                currentSongId = positionPrefs.getLong(SongIdKey, -1),
                positionMs = positionPrefs.getLong(PositionKey, 0),
                source = QueueSource.decode(queuePrefs.getString(SourceKey, null)),
            )
        } catch (e: ClassCastException) {
            // A value of another type, e.g. from an earlier build: nothing to come back to, rather
            // than a crash on every start.
            null
        }
}
