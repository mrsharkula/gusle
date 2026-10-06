package com.sharkula.gusle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.ui.home.HomeScreen
import com.sharkula.gusle.ui.songs.AllSongsScreen

/** The app's screens. Arguments a screen needs later become properties of its key. */
sealed interface Screen {
    data object Home : Screen
    data object AllSongs : Screen
}

/** Opens [screen] unless it's already on top, so a double tap can't stack it twice. */
private fun MutableList<Screen>.open(screen: Screen) {
    if (lastOrNull() != screen) add(screen)
}

/**
 * App navigation over the [songs] on the watch. Swiping right (or the back button) returns to
 * the previous screen.
 */
@Composable
fun GusleNavHost(songs: List<Song>) {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategy = rememberSwipeDismissableSceneStrategy(),
        entryProvider = entryProvider {
            entry<Screen.Home> {
                HomeScreen(
                    songCount = songs.size,
                    onAllSongs = { backStack.open(Screen.AllSongs) },
                    onSettings = {},
                )
            }
            entry<Screen.AllSongs> {
                // Both start playing once playback exists.
                AllSongsScreen(songs, onShuffleAll = {}, onSong = {})
            }
        },
    )
}
