package com.sharkula.gusle.playback

/**
 * What the player does with its queue once a song in it is deleted from the watch (see
 * [afterDeleting]).
 */
sealed interface AfterDeleting {
    /** The queue had no other song: it stops and forgets it, as Stop playing does. */
    data object NothingLeft : AfterDeleting

    /**
     * Its items at [remove] go, highest first, so the indices of those still to go don't move. If
     * it's on one of them, it first goes to the item at [moveTo], from its start, and pauses there
     * if [pause]; [moveTo] is null when it stays on its song.
     */
    data class Remove(val remove: List<Int>, val moveTo: Int?, val pause: Boolean) : AfterDeleting
}

/**
 * What a queue of [songIds] (the player's items' songs, in its order), playing in [playOrder] and on
 * the item at [current], does once the song [deleted] is gone from the watch; null when it isn't in
 * it. Every item of the song goes, as a song can be in it twice. If it was on one, it moves on by the
 * same rule as a saved queue ([moveOnFrom]): to the next one in play order that isn't the song, which
 * plays if it was playing; after the last, back to the nearest one before it, paused at its start.
 */
fun afterDeleting(songIds: List<Long?>, playOrder: List<Int>, current: Int, deleted: Long): AfterDeleting? {
    val remove = songIds.indices.filter { songIds[it] == deleted }
    if (remove.isEmpty()) return null
    if (remove.size == songIds.size) return AfterDeleting.NothingLeft
    val highestFirst = remove.asReversed()
    if (songIds.getOrNull(current) != deleted) return AfterDeleting.Remove(highestFirst, moveTo = null, pause = false)
    val isLeft = { index: Int -> songIds[index] != deleted }
    // Some other song is left, so there's one to go to, unless the order missed it.
    val move = moveOnFrom(playOrder, current, isLeft) ?: MoveOn(songIds.indices.first(isLeft), wentBack = true)
    return AfterDeleting.Remove(highestFirst, move.to, pause = move.wentBack)
}
