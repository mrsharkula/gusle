package com.sharkula.gusle.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

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
