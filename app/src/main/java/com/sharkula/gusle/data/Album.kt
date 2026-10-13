package com.sharkula.gusle.data

import java.text.Collator

/** An album and its songs, in track order, as Albums lists them. */
data class Album(
    /**
     * What finds the album again when the songs are read anew ([albumKey]); null for the songs
     * without an album tag.
     */
    val key: String?,
    /** As most of its songs spell it; null for the songs without an album tag. */
    val name: String?,
    /** Its album artist, or else its songs' artist, as Artists spells them; null when there's neither. */
    val artist: String?,
    /** Whether its songs are by different artists, which only the songs without an album can be. */
    val variousArtists: Boolean,
    val songs: List<Song>,
)

/**
 * What the album of [song] is known by, or null when it has no album tag: the album's name and its
 * album artist, or without one, the song's own artist, each as [nameKey] tells names apart. So two
 * artists' albums of the same name are two, also without album artist tags, and tags that differ only
 * in case or spaces are one album. A song by "A feat. B" on an album without album artist tags is on
 * an album of its own.
 */
fun albumKey(song: Song): String? {
    val album = song.album ?: return null
    val artist = (song.albumArtist ?: song.artist)?.let(::nameKey).orEmpty()
    // Names have no line breaks, so one tells the two apart.
    return artist + "\n" + nameKey(album)
}

/** The album's name in its [key] ([albumKey]), in lower case, e.g. for an album no longer on the watch. */
fun albumNameIn(key: String): String = key.substringAfter('\n')

/**
 * The [songs] (A–Z by title) by album ([albumKey]): A–Z by name with [collator], then by artist, and
 * the songs without an album last, as one. The albums' artists are spelled as in [artists] (the same
 * songs by artist). Each album's songs are in disc and track order, then those without a track
 * number, A–Z.
 */
fun groupByAlbum(songs: List<Song>, artists: List<Artist>, collator: Collator): List<Album> {
    val artistsByKey = artists.associateBy { it.key }
    val byKey = LinkedHashMap<String?, MutableList<Song>>()
    for (song in songs) byKey.getOrPut(albumKey(song)) { ArrayList() } += song
    val unknown = byKey.remove(null)
    val named = byKey.map { (key, songs) ->
        val artistKey = key!!.substringBefore('\n').ifEmpty { null }
        Album(
            key = key,
            name = mostCommonSpelling(songs.map { it.album!! }),
            artist = artistKey?.let {
                // An album artist without songs of their own, e.g. "Various Artists", isn't in Artists.
                artistsByKey[it]?.name ?: mostCommonSpelling(songs.mapNotNull { song -> song.albumArtist })
            },
            variousArtists = false,
            songs = inTrackOrder(songs),
        )
    }.sortedWith(
        compareBy(collator) { album: Album -> album.name }
            .thenBy(collator) { it.artist.orEmpty() }
            .thenBy { it.key },
    )
    return if (unknown == null) named else named + unknownAlbum(unknown, artistsByKey)
}

/**
 * The songs without an album, as one: under the artist they share, as far as they have one, or
 * "Various artists". Their album artists don't count: a few say nothing about the rest.
 */
private fun unknownAlbum(songs: List<Song>, artistsByKey: Map<String?, Artist>): Album {
    val artistKeys = songs.mapNotNullTo(HashSet()) { it.artist?.let(::artistKey) }
    return Album(
        key = null,
        name = null,
        artist = artistKeys.singleOrNull()?.let { artistsByKey[it]?.name },
        variousArtists = artistKeys.size > 1,
        songs = inTrackOrder(songs),
    )
}

/**
 * [songs] (A–Z) in disc and track order, then those without a track number, still A–Z. A track
 * without a disc number (under 1000; MediaStore keeps disc × 1000 + track) counts as on disc 1.
 */
private fun inTrackOrder(songs: List<Song>): List<Song> =
    songs.sortedWith(compareBy(nullsLast()) { song -> song.track?.let { if (it < 1000) 1000 + it else it } })
