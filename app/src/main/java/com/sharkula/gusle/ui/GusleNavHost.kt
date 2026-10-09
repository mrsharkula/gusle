package com.sharkula.gusle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.playingSong
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.playingSongColors
import com.sharkula.gusle.ui.home.HomeScreen
import com.sharkula.gusle.ui.nowplaying.NowPlayingScreen
import com.sharkula.gusle.ui.playback.playFrom
import com.sharkula.gusle.ui.playback.rememberPlayerConnection
import com.sharkula.gusle.ui.playback.shuffleAll
import com.sharkula.gusle.ui.queue.QueueScreen
import com.sharkula.gusle.ui.songs.AllSongsScreen
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlinx.serialization.Serializable

/** The app's screens. Arguments a screen needs later become properties of its key. */
@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object AllSongs : Screen

    @Serializable
    data object NowPlaying : Screen

    @Serializable
    data object Queue : Screen
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
    val player = rememberPlayerConnection()

    /** Starts playing (connecting first if needed), and opens Now playing on it. */
    fun play(action: Player.() -> Unit) {
        player.withPlayer(action)
        go(Screen.NowPlaying)
    }

    fun slidesIn(screen: Screen) = if (slidingIn == screen) SlideIn else emptyMap()

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
                    HomeScreen(
                        songCount = songs.list.size,
                        playing = playing,
                        isPlaying = isPlaying,
                        // It goes on as it is.
                        onPlaying = { go(Screen.NowPlaying) },
                        onAllSongs = { go(Screen.AllSongs) },
                        onSettings = {},
                    )
                }
            }
            entry<Screen.AllSongs>(metadata = slidesIn(Screen.AllSongs)) {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    AllSongsScreen(
                        songs.list,
                        playing = playing,
                        isPlaying = isPlaying,
                        onShuffleAll = { play { shuffleAll(songs.list) } },
                        onSong = { index -> play { playFrom(songs.list, index) } },
                        // Rather than starting it again: on a watch, taps happen by accident.
                        onPlayingSong = { go(Screen.NowPlaying) },
                    )
                }
            }
            entry<Screen.NowPlaying>(metadata = slidesIn(Screen.NowPlaying)) {
                NowPlayingScreen(player, songs.byId, onQueue = { go(Screen.Queue) })
            }
            entry<Screen.Queue>(metadata = slidesIn(Screen.Queue)) {
                PlayingSongTheme(songs) { _, _ ->
                    // Its song is Now playing's: tapping it goes back there, also when tapped twice.
                    QueueScreen(player, songs.byId, onPlayingSong = { go(Screen.NowPlaying) })
                }
            }
        },
    )
}

/**
 * A screen that shows the player's song, in its cover's colors, without connecting to the player.
 * [content] gets the song, playing or paused, or null when there's none, or it's no longer among
 * the [songs] (deleted while playing). Each such screen follows the player itself, so a change
 * there composes just that screen again, not the navigation around it.
 */
@Composable
private fun PlayingSongTheme(songs: Songs.Loaded, content: @Composable (playing: Song?, isPlaying: Boolean) -> Unit) {
    val playingSong by LocalContext.current.playingSong.collectAsStateWithLifecycle()
    val song = playingSong?.let { songs.byId[it.songId] }
    GusleTheme(colorScheme = playingSongColors(song?.let(::SongCover))) {
        content(song, playingSong?.isPlaying == true)
    }
}
