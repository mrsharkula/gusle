package com.sharkula.gusle.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.R
import com.sharkula.gusle.data.FavoriteSongs
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playback.PlayerPlace
import com.sharkula.gusle.playback.QueueSource
import com.sharkula.gusle.playerPlace
import com.sharkula.gusle.playingSong
import com.sharkula.gusle.playlists
import com.sharkula.gusle.themeChoice
import com.sharkula.gusle.ui.albums.AlbumsScreen
import com.sharkula.gusle.ui.artists.ArtistsScreen
import com.sharkula.gusle.ui.components.MessageScreen
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.playingSongColors
import com.sharkula.gusle.ui.favorites.rememberFavorites
import com.sharkula.gusle.ui.favorites.rememberSetFavorite
import com.sharkula.gusle.ui.home.HomeScreen
import com.sharkula.gusle.ui.nowplaying.NowPlayingScreen
import com.sharkula.gusle.ui.nowplaying.SeekScreen
import com.sharkula.gusle.ui.playback.playFrom
import com.sharkula.gusle.ui.playback.rememberPlayerConnection
import com.sharkula.gusle.ui.playback.shuffleAll
import com.sharkula.gusle.ui.playlists.PlaylistsScreen
import com.sharkula.gusle.ui.playlists.rememberPlaylistNaming
import com.sharkula.gusle.ui.playlists.rememberPlaylists
import com.sharkula.gusle.ui.queue.QueueScreen
import com.sharkula.gusle.ui.settings.AboutScreen
import com.sharkula.gusle.ui.settings.SettingsScreen
import com.sharkula.gusle.ui.settings.ThemeScreen
import com.sharkula.gusle.ui.songs.DeletionStep
import com.sharkula.gusle.ui.songs.SongActionsScreen
import com.sharkula.gusle.ui.songs.SongListScreen
import com.sharkula.gusle.ui.songs.rememberSongDeletion
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.LocalAppTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The app's screens. Arguments a screen needs later become properties of its key. */
@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object AllSongs : Screen

    @Serializable
    data object Artists : Screen

    /**
     * An artist's songs, by the artist's [com.sharkula.gusle.data.Artist.key]: null for the songs
     * without an artist.
     */
    @Serializable
    data class Artist(val key: String?) : Screen

    @Serializable
    data object Albums : Screen

    /**
     * An album's songs, by the album's [com.sharkula.gusle.data.Album.key]: null for the songs
     * without an album.
     */
    @Serializable
    data class Album(val key: String?) : Screen

    /** The favorite songs. */
    @Serializable
    data object Favorites : Screen

    @Serializable
    data object Playlists : Screen

    /** A playlist's songs, by its [com.sharkula.gusle.data.Playlist.id]. */
    @Serializable
    data class Playlist(val id: Long) : Screen

    @Serializable
    data object NowPlaying : Screen

    @Serializable
    data object Queue : Screen

    /** Seeking in the playing song, opened from Now playing. */
    @Serializable
    data object Seek : Screen

    /**
     * What can be done to the song with [songId], opened by holding it: at [place], for the actions
     * that will need to know, e.g. which of a playlist's entries it is when the song is there twice.
     */
    @Serializable
    data class SongActions(val songId: Long, val place: SongPlace = SongPlace.Library) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Theme : Screen

    @Serializable
    data object About : Screen
}

/** Where a song was held to open its actions ([Screen.SongActions]). */
@Serializable
sealed interface SongPlace {
    /**
     * The song itself, wherever it was: a list of songs (All songs, an artist's or an album's songs,
     * Favorites), Home's playing song, Now playing or the seek screen.
     */
    @Serializable
    data object Library : SongPlace

    /**
     * The player's item [window] in the Queue, where a song can be twice. Nothing reads it yet (Delete
     * deletes the song itself, so every item of it goes); an action that will must check the item
     * there is still this song, as the queue may have changed.
     */
    @Serializable
    data class InQueue(val window: Int) : SongPlace

    /**
     * The entry with [entryId] ([com.sharkula.gusle.data.PlaylistEntry.id]) of the playlist with
     * [playlistId], where a song can be twice too. Nothing reads it yet; an action that will must
     * check the playlist still has the entry, as it may have changed.
     */
    @Serializable
    data class InPlaylist(val playlistId: Long, val entryId: Long) : SongPlace
}

/**
 * Saves the back stack. [Screen] is sealed, so its serializer knows every screen, and nothing is
 * looked up by reflection, which R8 could break.
 */
private val BackStackSaving = NavBackStackSerializer(Screen.serializer())

/**
 * Asks the navigation to open Now playing, e.g. when Gusle is opened from the watch's media
 * controls. The navigation takes it once it's shown.
 */
@Stable
class NowPlayingRequest {
    /** From the request until Now playing is open, or the request is dropped. */
    var pending by mutableStateOf(false)
}

/**
 * Opens [screen] on top, or goes back to it if it's lower down already: a screen is never in the
 * stack twice, since the screens below the top one are kept composed. A double tap doesn't stack it
 * twice either. Returns whether it was added.
 */
private fun MutableList<Screen>.open(screen: Screen): Boolean {
    val at = indexOf(screen)
    if (at < 0) return add(screen)
    while (lastIndex > at) removeAt(lastIndex)
    return false
}

/**
 * Closes the top screen, but never Home: two back events in one frame both arrive before
 * NavDisplay sees that Home is alone, and an empty back stack makes it throw. Returns whether a
 * screen closed.
 */
private fun MutableList<Screen>.close(): Boolean {
    if (size == 1) return false
    removeAt(lastIndex)
    return true
}

/**
 * App navigation over the [songs] on the watch. Swiping right (or the back button) returns to
 * the previous screen, and then calls [onWentBack]. [openNowPlaying] opens Now playing on top of
 * the screen that's shown.
 */
@Composable
fun GusleNavHost(songs: Songs.Loaded, openNowPlaying: NowPlayingRequest, onWentBack: () -> Unit) {
    // Saved, so when Android recreates Gusle (a font size change, or its process restored), the
    // screens come back as they were, each with its own saved state.
    val backStack = rememberSerializable(serializer = BackStackSaving) {
        // Asked for at launch, it's there from the first frame, with Home behind it.
        NavBackStack<Screen>(Screen.Home).apply {
            if (openNowPlaying.pending) open(Screen.NowPlaying)
        }
    }
    // The screen a tap opened last, which slides in over the one below. None after going back, or
    // when Now playing opens on request: Gusle is only just coming on the screen then, so it's
    // there at once. Not saved either: a restored stack shows its top screen at once.
    var slidingIn by remember { mutableStateOf<Screen?>(null) }

    /** Opens [screen] for a tap. */
    fun go(screen: Screen) {
        if (backStack.open(screen)) slidingIn = screen
    }

    // Taken here rather than above, so it isn't lost if that first composition is thrown away.
    LaunchedEffect(openNowPlaying) {
        snapshotFlow { openNowPlaying.pending }.collect { pending ->
            if (pending) {
                slidingIn = null
                backStack.open(Screen.NowPlaying)
                openNowPlaying.pending = false
            }
        }
    }
    // An artist or album whose songs are all gone when they're read anew closes, wherever it is in
    // the stack, rather than showing no songs; so does one the stack was saved with, a song's
    // actions once the song is gone, and a playlist once it's deleted (as soon as the playlists are
    // read, for one the stack was saved with). Followed here rather than read as state, so a change
    // doesn't compose the navigation again.
    val playlistStore = LocalContext.current.playlists
    LaunchedEffect(songs, playlistStore) {
        playlistStore.all.collect { playlists ->
            backStack.removeAll { screen ->
                when (screen) {
                    is Screen.Artist -> screen.key !in songs.artistsByKey
                    is Screen.Album -> screen.key !in songs.albumsByKey
                    is Screen.SongActions -> screen.songId !in songs.byId
                    is Screen.Playlist -> playlists != null && playlists.none { it.id == screen.id }
                    else -> false
                }
            }
        }
    }
    val player = rememberPlayerConnection()

    /** Starts playing (connecting first if needed), and opens Now playing on it. */
    fun play(action: Player.() -> Unit) {
        player.withPlayer(action)
        go(Screen.NowPlaying)
    }

    fun slidesIn(screen: Screen) = if (slidingIn == screen) SlideIn else emptyMap()

    /**
     * Opens the actions of a [song] that was held, at [place], on top of the screen it was held on.
     * The same ones lower down go, rather than closing the screens above them to go back there:
     * e.g. Now playing, opened from the watch's media controls over the actions of its song.
     */
    fun songActions(song: Song, place: SongPlace = SongPlace.Library) {
        val screen = Screen.SongActions(song.id, place)
        backStack.remove(screen)
        go(screen)
    }

    /**
     * The songs in [list] under their [title], played from the tapped one, or shuffled, as songs
     * from [source]. The player's row ([playingKey]; by default the player's song's, [playing])
     * opens Now playing. The favorites among them have their hearts, but not in the favorites
     * themselves. In a playlist, where a song can be twice, its rows are its [entryIds]: they tell
     * the rows apart, go with the songs into the player, and say where a song was held.
     */
    @Composable
    fun SongList(
        title: String,
        source: QueueSource,
        list: List<Song>,
        playing: Song?,
        isPlaying: Boolean,
        favorites: FavoriteSongs = rememberFavorites(),
        entryIds: List<Long>? = null,
        playingKey: Any? = playing?.id,
    ) {
        SongListScreen(
            title,
            list,
            playingKey = playingKey,
            isPlaying = isPlaying,
            favorites = favorites,
            onShuffleAll = { play { shuffleAll(list, source, entryIds) } },
            onSong = { index -> play { playFrom(list, index, source, entryIds) } },
            // Rather than starting it again: on a watch, taps happen by accident.
            onPlayingSong = { go(Screen.NowPlaying) },
            onFavorite = rememberSetFavorite(),
            onSongActions = { index, song ->
                val place = if (entryIds != null && source is QueueSource.Playlist) {
                    SongPlace.InPlaylist(source.id, entryIds[index])
                } else {
                    SongPlace.Library
                }
                songActions(song, place)
            },
            heartsShown = source != QueueSource.Favorites,
            rowKey = if (entryIds != null) ({ index, _ -> entryIds[index] }) else ({ _, song -> song.id }),
        )
    }

    /**
     * The songs of a [group] of them, e.g. an artist, under its [title], as songs from [source]. Once
     * the group's songs are all gone, its screen closes (see above) showing what it showed, rather
     * than nothing; one the stack was saved with has nothing to show meanwhile.
     */
    @Composable
    fun <G : Any> GroupSongs(
        group: G?,
        title: @Composable (G) -> String,
        source: QueueSource,
        songsOf: (G) -> List<Song>,
    ) {
        val shown = lastNotNull(group) ?: return
        PlayingSongTheme(songs) { playing, isPlaying ->
            SongList(title(shown), source, songsOf(shown), playing = playing, isPlaying = isPlaying)
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.close()) {
                slidingIn = null
                onWentBack()
            }
        },
        sceneStrategies = listOf(rememberStackSceneStrategy()),
        entryProvider = entryProvider {
            entry<Screen.Home> {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    val favorites = rememberFavorites()
                    val playlists = rememberPlaylists()
                    HomeScreen(
                        songCount = songs.list.size,
                        // The songs without an artist aren't an artist's.
                        artistCount = songs.artists.count { it.name != null },
                        // Nor the songs without an album an album.
                        albumCount = songs.albums.count { it.name != null },
                        favoriteCount = remember(favorites, songs) { favorites.countIn(songs.byId) },
                        playlistCount = playlists?.size ?: 0,
                        playing = playing,
                        isPlaying = isPlaying,
                        playingIsFavorite = playing != null && playing in favorites,
                        // It goes on as it is.
                        onPlaying = { go(Screen.NowPlaying) },
                        onPlayingActions = { playing?.let { songActions(it) } },
                        onAllSongs = { go(Screen.AllSongs) },
                        onArtists = { go(Screen.Artists) },
                        onAlbums = { go(Screen.Albums) },
                        onFavorites = { go(Screen.Favorites) },
                        onPlaylists = { go(Screen.Playlists) },
                        onSettings = { go(Screen.Settings) },
                    )
                }
            }
            entry<Screen.AllSongs>(metadata = slidesIn(Screen.AllSongs)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    SongList(
                        title = pluralStringResource(R.plurals.song_count, songs.list.size, songs.list.size),
                        source = QueueSource.AllSongs,
                        list = songs.list,
                        playing = playing,
                        isPlaying = isPlaying,
                    )
                }
            }
            entry<Screen.Artists>(metadata = slidesIn(Screen.Artists)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    ArtistsScreen(
                        songs.artists,
                        playing = playing,
                        isPlaying = isPlaying,
                        onArtist = { go(Screen.Artist(it.key)) },
                    )
                }
            }
            entry<Screen.Artist>(metadata = { slidesIn(it) }) { screen ->
                GroupSongs(
                    songs.artistsByKey[screen.key],
                    title = { it.name ?: stringResource(R.string.unknown_artist) },
                    source = QueueSource.Artist(screen.key),
                    songsOf = { it.songs },
                )
            }
            entry<Screen.Albums>(metadata = slidesIn(Screen.Albums)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    AlbumsScreen(
                        songs.albums,
                        playing = playing,
                        isPlaying = isPlaying,
                        onAlbum = { go(Screen.Album(it.key)) },
                    )
                }
            }
            entry<Screen.Album>(metadata = { slidesIn(it) }) { screen ->
                GroupSongs(
                    songs.albumsByKey[screen.key],
                    title = { it.name ?: stringResource(R.string.unknown_album) },
                    source = QueueSource.Album(screen.key),
                    songsOf = { it.songs },
                )
            }
            entry<Screen.Favorites>(metadata = slidesIn(Screen.Favorites)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    val favorites = rememberFavorites()
                    val list = remember(favorites, songs) { favorites.newestFirst(songs.byId) }
                    if (list.isEmpty()) {
                        MessageScreen(
                            icon = R.drawable.ic_favorite,
                            title = stringResource(R.string.no_favorites_title),
                            text = stringResource(R.string.no_favorites_text),
                            action = stringResource(R.string.all_songs),
                            onAction = { go(Screen.AllSongs) },
                            actionIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    } else {
                        SongList(
                            title = stringResource(R.string.favorites),
                            source = QueueSource.Favorites,
                            list = list,
                            playing = playing,
                            isPlaying = isPlaying,
                            favorites = favorites,
                        )
                    }
                }
            }
            entry<Screen.Playlists>(metadata = slidesIn(Screen.Playlists)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    val place = rememberPlayerPlace()
                    // Nothing for the moment until they're read, rather than "No playlists yet".
                    val playlists = rememberPlaylists() ?: return@PlayingSongTheme
                    val scope = rememberCoroutineScope()
                    // A new playlist opens, empty, once it's named.
                    val naming = rememberPlaylistNaming { name ->
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { go(Screen.Playlist(playlistStore.create(name))) }
                    }
                    PlaylistsScreen(
                        playlists,
                        songs.byId,
                        playingFrom = (place?.source as? QueueSource.Playlist)?.id?.takeIf { playing != null },
                        isPlaying = isPlaying,
                        onPlaylist = { go(Screen.Playlist(it.id)) },
                        onNewPlaylist = naming::ask,
                    )
                }
            }
            // Once the playlist is deleted, it closes (see above) showing what it showed; one a saved
            // stack brings back shows nothing meanwhile.
            entry<Screen.Playlist>(metadata = { slidesIn(it) }) { screen ->
                val playlist = lastNotNull(rememberPlaylists()?.firstOrNull { it.id == screen.id }) ?: return@entry
                PlayingSongTheme(songs) { playing, isPlaying ->
                    val place = rememberPlayerPlace()
                    val shown = remember(playlist, songs) { playlist.songsIn(songs.byId) }
                    if (shown.isEmpty()) {
                        MessageScreen(
                            icon = R.drawable.ic_queue_music,
                            title = playlist.name,
                            text = stringResource(R.string.add_to_playlist_hint),
                            action = stringResource(R.string.all_songs),
                            onAction = { go(Screen.AllSongs) },
                            actionIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_music_note),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    } else {
                        val list = remember(shown) { shown.map { it.song } }
                        val entryIds = remember(shown) { shown.map { it.entryId } }
                        // By its entry, not its song, as a song can be twice: the one the player is
                        // on, while its queue came from here.
                        val fromHere = (place?.source as? QueueSource.Playlist)?.id == playlist.id
                        SongList(
                            title = playlist.name,
                            source = QueueSource.Playlist(playlist.id, playlist.name),
                            list = list,
                            playing = playing,
                            isPlaying = isPlaying,
                            entryIds = entryIds,
                            playingKey = place?.entryId?.takeIf { fromHere && playing != null },
                        )
                    }
                }
            }
            entry<Screen.NowPlaying>(metadata = slidesIn(Screen.NowPlaying)) {
                NowPlayingScreen(
                    player,
                    songs.byId,
                    onQueue = { go(Screen.Queue) },
                    onSeek = { go(Screen.Seek) },
                    onSongActions = { songActions(it) },
                )
            }
            entry<Screen.Seek>(metadata = slidesIn(Screen.Seek)) {
                SeekScreen(player, songs.byId, onSongActions = { songActions(it) })
            }
            entry<Screen.Queue>(metadata = slidesIn(Screen.Queue)) {
                PlayingSongTheme(songs) { _, _ ->
                    // Its song is Now playing's: tapping it goes back there, also when tapped twice.
                    QueueScreen(
                        player,
                        songs.byId,
                        onPlayingSong = { go(Screen.NowPlaying) },
                        onSongActions = { song, window -> songActions(song, SongPlace.InQueue(window)) },
                    )
                }
            }
            // In the colors of the screen it was opened from: the playing song's. Once the song is gone
            // it closes (see above), still showing it as it goes; one a saved stack brings back shows
            // nothing until then. Deleted, it keeps its colors and heart until it has closed, though
            // the player moves on and the song stops being a favorite meanwhile.
            entry<Screen.SongActions>(metadata = { slidesIn(it) }) { screen ->
                // First: Android's answer must arrive even when Gusle is restored without the song.
                val deletion = rememberSongDeletion(screen.songId)
                val song = lastNotNull(songs.byId[screen.songId]) ?: return@entry
                val deleted = deletion.step == DeletionStep.Deleted
                PlayingSongTheme(songs) { _, _ ->
                    MaterialTheme(colorScheme = keptWhile(deleted, MaterialTheme.colorScheme)) {
                        val favorites = rememberFavorites()
                        val setFavorite = rememberSetFavorite()
                        SongActionsScreen(
                            song,
                            isFavorite = keptWhile(deleted, song in favorites),
                            onFavorite = if (deleted) null else { favorite -> setFavorite(song, favorite) },
                            deletion = deletion,
                            onTop = backStack.lastOrNull() == screen,
                        )
                    }
                }
            }
            // In the playing song's colors too, so opening them from Home doesn't change the colors.
            entry<Screen.Settings>(metadata = slidesIn(Screen.Settings)) {
                val activity = LocalActivity.current
                // Also a song deleted while playing, which PlayingSongTheme leaves out: there's still
                // one to stop then.
                val playingSong by LocalContext.current.playingSong.collectAsStateWithLifecycle()
                PlayingSongTheme(songs) { playing, _ ->
                    SettingsScreen(
                        songCount = songs.list.size,
                        hasSong = playingSong != null,
                        songTitle = playing?.title,
                        // Gusle closes once the song's gone; it opens next time with nothing playing.
                        onStopPlaying = { player.stopAndForget { activity?.finish() } },
                        onTheme = { go(Screen.Theme) },
                        onAbout = { go(Screen.About) },
                    )
                }
            }
            entry<Screen.Theme>(metadata = slidesIn(Screen.Theme)) {
                val themes = LocalContext.current.themeChoice
                PlayingSongTheme(songs) { _, _ ->
                    ThemeScreen(current = LocalAppTheme.current, onChoose = themes::choose)
                }
            }
            entry<Screen.About>(metadata = slidesIn(Screen.About)) {
                PlayingSongTheme(songs) { _, _ ->
                    AboutScreen()
                }
            }
        },
    )
}

/**
 * [value], or while it's null, the last value it had that wasn't: e.g. an artist whose songs are all
 * gone, until their screen has closed.
 */
@Composable
private fun <T : Any> lastNotNull(value: T?): T? = keptWhile(held = value == null, value)

/** [value], or while [held], the last value it had before. */
@Composable
private fun <T> keptWhile(held: Boolean, value: T): T {
    val kept = remember { Kept(value) }
    SideEffect { if (!held) kept.value = value }
    return if (held) kept.value else value
}

private class Kept<T>(var value: T)

/**
 * A screen in the colors of the player's song's cover, without connecting to the player.
 * [content] gets the song, playing or paused, or null when there's none, or it's no longer among
 * the [songs] (deleted while playing). Each such screen follows the player itself, so a change
 * there composes just that screen again, not the navigation around it. Hidden below the top
 * screen, it follows the player a moment later ([heldWhileHidden]).
 */
@Composable
private fun PlayingSongTheme(songs: Songs.Loaded, content: @Composable (playing: Song?, isPlaying: Boolean) -> Unit) {
    val playingSong by LocalContext.current.playingSong.collectAsStateWithLifecycle()
    // Its colors too: they follow the song.
    val shownSong = heldWhileHidden(playingSong)
    val song = shownSong?.let { songs.byId[it.songId] }
    GusleTheme(colorScheme = playingSongColors(song?.let(::SongCover))) {
        content(song, shownSong?.isPlaying == true)
    }
}

/**
 * Where the player is ([PlayerPlace]), for the screens that show it (the playlists), followed like
 * the player's song: a moment later while hidden ([heldWhileHidden]).
 */
@Composable
private fun rememberPlayerPlace(): PlayerPlace? {
    val place by LocalContext.current.playerPlace.collectAsStateWithLifecycle()
    return heldWhileHidden(place)
}
