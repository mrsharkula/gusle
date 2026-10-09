package com.sharkula.gusle.ui.queue

import androidx.compose.runtime.Immutable

/** The song with [songId] at [place] in the play order, played by the player's item at [window]. */
@Immutable
data class QueueSong(val window: Int, val place: Int, val songId: Long)

/**
 * The player's songs in the order they play ([playOrder], the player's item indices; [shuffled] or
 * A–Z), as the Queue lists them: [songs] leaves out those no longer on the watch, and the others
 * keep their place.
 */
@Immutable
data class PlayerQueue(val playOrder: List<Int>, val shuffled: Boolean, val songs: List<QueueSong>) {
    private val places = IntArray(playOrder.size).also { places ->
        playOrder.forEachIndexed { place, window -> places[window] = place }
    }

    /** The place in the play order of the player's item at [window]; -1 if there's no such item. */
    fun placeOf(window: Int): Int = places.getOrElse(window) { -1 }

    companion object {
        /**
         * The queue playing in [playOrder], whose item at each window plays the song [songIdAt] that
         * window, as far as those songs are [onWatch].
         */
        fun of(
            playOrder: List<Int>,
            shuffled: Boolean,
            songIdAt: (window: Int) -> Long?,
            onWatch: (songId: Long) -> Boolean,
        ) =
            PlayerQueue(
                playOrder,
                shuffled,
                playOrder.mapIndexedNotNull { place, window ->
                    songIdAt(window)?.takeIf(onWatch)?.let { QueueSong(window, place, it) }
                },
            )
    }
}
