package com.sharkula.gusle.playback

import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastPlayedTest {
    /** The files on "disk", by name; a new [LastPlayed] on them is Gusle started again. */
    private val files = HashMap<String, FakePrefs>()

    private fun lastPlayed() = LastPlayed { name -> files.getOrPut(name) { FakePrefs() } }

    private fun LastPlayed.loaded() = runBlocking { load() }

    private val queue = SavedQueue(listOf(10L, 20L, 30L), shuffleOrder = listOf(2, 0, 1), current = 1, positionMs = 31_500)

    @Test
    fun comesBackAfterARestart() {
        lastPlayed().save(queue)
        assertEquals(queue, lastPlayed().loaded())
    }

    @Test
    fun hasNothingBeforeAnythingIsSaved() {
        assertNull(lastPlayed().loaded())
    }

    @Test
    fun writesOnlyThePositionWhileTheSongsStayTheSame() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.save(queue.copy(positionMs = 41_500))
        lastPlayed.save(queue.copy(positionMs = 41_500))
        assertEquals(1, files.getValue("last_played_queue").writes)
        assertEquals(2, files.getValue("last_played_position").writes)
        assertEquals(queue.copy(positionMs = 41_500), lastPlayed().loaded())
    }

    @Test
    fun forgetsTheShuffleOrderWhenShuffleGoesOff() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.save(queue.copy(shuffleOrder = null))
        assertEquals(queue.copy(shuffleOrder = null), lastPlayed().loaded())
    }

    @Test
    fun forgetsEverything() {
        val lastPlayed = lastPlayed()
        lastPlayed.save(queue)
        lastPlayed.forget()
        assertNull(lastPlayed.loaded())
        assertNull(lastPlayed().loaded())
        // And a later save stores the songs again, not only where it is.
        lastPlayed.save(queue)
        assertEquals(queue, lastPlayed().loaded())
    }

    @Test
    fun aSaveBeforeTheReadIsNewer() {
        lastPlayed().save(queue)
        val lastPlayed = lastPlayed()
        val newer = queue.copy(current = 2, positionMs = 0)
        lastPlayed.save(newer)
        assertEquals(newer, lastPlayed.loaded())
    }

    @Test
    fun aValueOfAnotherTypeReadsAsNothing() {
        lastPlayed().save(queue)
        files.getValue("last_played_position").values["song_id"] = "20"
        assertNull(lastPlayed().loaded())
    }

    /**
     * The order is saved as the songs' indices in the order they play, and restored with
     * DefaultShuffleOrder, which must then play them in that order.
     */
    @OptIn(UnstableApi::class)
    @Test
    fun aRestoredShuffleOrderPlaysAsSaved() {
        val saved = listOf(3, 0, 4, 2, 1)
        val restored = DefaultShuffleOrder(saved.toIntArray(), 7)
        val plays = generateSequence(restored.firstIndex) { restored.getNextIndex(it).takeIf { next -> next != C.INDEX_UNSET } }
        assertEquals(saved, plays.toList())
    }
}

/** SharedPreferences kept in memory, counting the writes. */
private class FakePrefs : SharedPreferences {
    val values = HashMap<String, Any>()
    var writes = 0

    override fun getAll(): Map<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?): String? = values[key]?.let { it as String } ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = throw UnsupportedOperationException()
    override fun getInt(key: String, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key]?.let { it as Boolean } ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    private inner class Editor : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private var clear = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            changes[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) values.clear()
            for ((key, value) in changes) if (value == null) values.remove(key) else values[key] = value
            writes++
        }
    }
}
