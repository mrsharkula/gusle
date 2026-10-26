package com.sharkula.gusle.ui.settings

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async

/**
 * A choice made in Settings and kept on disk by a stable ID: the app theme
 * ([com.sharkula.gusle.ui.theme.ThemeChoice]) and the jump step
 * ([com.sharkula.gusle.ui.nowplaying.JumpStepChoice]). The app keeps one of each, made as a screen
 * or the tile first needs it, and MainActivity provides the [current] value around every screen.
 * Use it from the main thread: it reads the disk once, off it, as soon as it's made, and writes in
 * the background. These are backed up and moved to a new watch, unlike the queue in
 * [com.sharkula.gusle.playback.LastPlayed]: a choice means the same on any watch.
 */
open class SavedChoice<T : Any> internal constructor(
    /** The key the choice is saved under, as the ID [idOf] gives. */
    private val key: String,
    /** What a value is saved as, so renaming or moving an entry doesn't change what was chosen. */
    private val idOf: (T) -> String,
    /** The value saved as an ID; the default for null, or for an ID that's no longer one of them. */
    private val fromId: (String?) -> T,
    /** Runs when another value is chosen, e.g. for the tile to follow it. */
    private val onChoose: () -> Unit,
    openPrefs: () -> SharedPreferences,
    /** Done with the value read from disk, still off the main thread, e.g. making a theme's colors. */
    private val prepare: (T) -> Unit = {},
) {
    private val prefs by lazy(openPrefs)

    // Starts right away, so it's usually ready before the first frame.
    private val reading = MainScope().async(Dispatchers.IO) { read().also(prepare) }

    /**
     * The chosen value, once it's been [load]ed or chosen; null until then. It's state: the screens
     * reading it follow a new choice.
     */
    var current: T? by mutableStateOf(null)
        private set

    /** The chosen value, read from disk the first time. */
    @MainThread
    suspend fun load(): T {
        current?.let { return it }
        val read = reading.await()
        // Unless one was chosen meanwhile: that's newer.
        return current ?: read.also { current = it }
    }

    /** Makes [value] the current one, and keeps it. */
    @MainThread
    fun choose(value: T) {
        if (value == current) return
        current = value
        // Written in the background (apply, not commit).
        prefs.edit { putString(key, idOf(value)) }
        onChoose()
    }

    private fun read(): T =
        try {
            fromId(prefs.getString(key, null))
        } catch (e: ClassCastException) {
            // A value of another type, e.g. from an earlier build: the default, rather than a crash
            // on every start.
            fromId(null)
        }
}
