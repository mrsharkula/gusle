package com.sharkula.gusle.data

import java.text.Collator
import java.util.Locale

/** An artist and their songs, A–Z by title, as Artists lists them. */
data class Artist(
    /** As most of their songs spell it; null for the songs without an artist tag. */
    val name: String?,
    val songs: List<Song>,
) {
    /** What finds the artist again when the songs are read anew ([artistKey]); null with no [name]. */
    val key: String? = name?.let(::artistKey)
}

/** Any kind of space, also a no-break one, which \s alone doesn't match. */
private val Spaces = Regex("[\\s\\p{Z}]+")

/** [name] as it's shown: without spaces at either end, and one between words. */
private fun tidy(name: String) = name.trim().replace(Spaces, " ")

/**
 * What an artist is known by. Tags that differ only in case or spaces, e.g. "sabaton" and
 * "Sabaton ", are one artist's. Anything else is an artist of its own: "A feat. B" isn't "A".
 */
fun artistKey(name: String): String = tidy(name).lowercase(Locale.ROOT)

/**
 * The [songs] by artist: A–Z by name with [collator], each artist's songs in the same order as
 * [songs], and the songs without an artist last, as one.
 */
fun groupByArtist(songs: List<Song>, collator: Collator): List<Artist> {
    val byKey = LinkedHashMap<String?, MutableList<Song>>()
    for (song in songs) byKey.getOrPut(song.artist?.let(::artistKey)) { ArrayList() } += song
    val unknown = byKey.remove(null)
    val named = byKey.values.map { Artist(spelling(it), it) }
        .sortedWith(compareBy(collator) { artist: Artist -> artist.name }.thenBy { it.key })
    return if (unknown == null) named else named + Artist(null, unknown)
}

/**
 * The name for an artist with [songs]: the way most of them spell it, or of those spelled as often,
 * the one that comes first by character code, which puts capitals first ("Sabaton", not "sabaton").
 */
private fun spelling(songs: List<Song>): String =
    songs.groupingBy { tidy(it.artist!!) }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .first().key
