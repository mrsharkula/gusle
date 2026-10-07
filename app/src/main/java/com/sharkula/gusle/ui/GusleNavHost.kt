package com.sharkula.gusle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
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
import com.sharkula.gusle.ui.songs.AllSongsScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/** The app's screens. Arguments a screen needs later become properties of its key. */
sealed interface Screen {
    data object Home : Screen
    data object AllSongs : Screen
    data object NowPlaying : Screen
}

/** Opens [screen] unless it's already on top, so a double tap can't stack it twice. */
private fun MutableList<Screen>.open(screen: Screen) {
    if (lastOrNull() != screen) add(screen)
}

/**
 * Closes the top screen, but never Home: two back events in one frame both arrive before
 * NavDisplay sees that Home is alone, and an empty back stack makes it throw.
 */
private fun MutableList<Screen>.close() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * App navigation over the [songs] on the watch. Swiping right (or the back button) returns to
 * the previous screen.
 */
@Composable
fun GusleNavHost(songs: Songs.Loaded) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val player = rememberPlayerConnection()

    /** Starts playing (connecting first if needed), and opens Now playing on it. */
    fun play(action: Player.() -> Unit) {
        player.withPlayer(action)
        backStack.open(Screen.NowPlaying)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.close() },
        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
        entryProvider = entryProvider {
            entry<Screen.Home> {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    HomeScreen(
                        songCount = songs.list.size,
                        playing = playing,
                        isPlaying = isPlaying,
                        // It goes on as it is.
                        onPlaying = { backStack.open(Screen.NowPlaying) },
                        onAllSongs = { backStack.open(Screen.AllSongs) },
                        onSettings = {},
                    )
                }
            }
            entry<Screen.AllSongs> {
                PlayingSongTheme(songs) { playing, isPlaying ->
                    AllSongsScreen(
                        songs.list,
                        playing = playing,
                        isPlaying = isPlaying,
                        onShuffleAll = { play { shuffleAll(songs.list) } },
                        onSong = { index -> play { playFrom(songs.list, index) } },
                        // Rather than starting it again: on a watch, taps happen by accident.
                        onPlayingSong = { backStack.open(Screen.NowPlaying) },
                    )
                }
            }
            entry<Screen.NowPlaying> {
                NowPlayingScreen(player, songs.byId)
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
