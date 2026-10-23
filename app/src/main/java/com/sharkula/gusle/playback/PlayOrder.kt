package com.sharkula.gusle.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlin.random.Random

/**
 * The timeline's windows (the player's songs) in the order they play: as they are (A–Z), or the
 * order they play in while [shuffled]. Repeat is never on, so it ends with the last one.
 */
fun Timeline.playOrder(shuffled: Boolean): List<Int> = buildList(windowCount) {
    var window = getFirstWindowIndex(shuffled)
    while (window != C.INDEX_UNSET) {
        add(window)
        window = getNextWindowIndex(window, Player.REPEAT_MODE_OFF, shuffled)
    }
}

/**
 * Where to go from the song at [from] when it can't be played or is gone: the next song in
 * [playOrder] that [isPlayable], or going [backwards], the nearest one before it that is. Null when
 * there's none: nothing loops.
 */
fun nextPlayable(playOrder: List<Int>, from: Int, backwards: Boolean, isPlayable: (Int) -> Boolean): Int? {
    val at = playOrder.indexOf(from)
    if (at < 0) return null
    val ahead = if (backwards) playOrder.subList(0, at).asReversed() else playOrder.subList(at + 1, playOrder.size)
    return ahead.firstOrNull(isPlayable)
}

/** Where a queue goes from a song that's gone ([moveOnFrom]): to the song at [to], back to one before it if [wentBack]. */
data class MoveOn(val to: Int, val wentBack: Boolean)

/**
 * Where a queue playing in [playOrder] goes from the song at [current] once that song is gone: to
 * the next one in play order that [isLeft], or with none after it, back to the nearest one before it.
 * Null when none is left. The one rule for a saved queue ([SavedQueue.keepOnly]) and the player
 * ([afterDeleting]).
 */
fun moveOnFrom(playOrder: List<Int>, current: Int, isLeft: (Int) -> Boolean): MoveOn? {
    nextPlayable(playOrder, current, backwards = false, isLeft)?.let { return MoveOn(it, wentBack = false) }
    return nextPlayable(playOrder, current, backwards = true, isLeft)?.let { MoveOn(it, wentBack = true) }
}

/** A shuffled order of [count] songs that starts with the one at [first]: the rest follow at random. */
fun shuffledFrom(first: Int, count: Int, random: Random): IntArray =
    intArrayOf(first) + (0 until count).filter { it != first }.shuffled(random)
