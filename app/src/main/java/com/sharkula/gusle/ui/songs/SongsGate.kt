package com.sharkula.gusle.ui.songs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Song
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.ui.components.MessageScreen
import com.sharkula.gusle.ui.theme.GusleTheme
import kotlinx.coroutines.launch

/**
 * Shows [content] with the songs on the watch once they're read, or "No songs yet" if there are
 * none. Reads them the first time it's shown, so only once access is on.
 */
@Composable
fun SongsGate(library: MusicLibrary, content: @Composable (songs: List<Song>) -> Unit) {
    LaunchedEffect(library) { library.load() }
    val songs by library.songs.collectAsStateWithLifecycle()
    val scanning by library.scanning.collectAsStateWithLifecycle()

    // Until then the screen stays black: reading takes milliseconds, too short for anything else.
    // (No access can't last here: load() runs once access is on.)
    val loaded = songs as? Songs.Loaded ?: return
    if (loaded.list.isEmpty()) {
        NoSongsScreen(scanning = scanning, onRescan = library::rescan)
    } else {
        content(loaded.list)
    }
}

private const val TurnMillis = 1100

@Composable
private fun NoSongsScreen(scanning: Boolean, onRescan: () -> Unit) {
    val rotation = remember { Animatable(0f) }
    val stillScanning by rememberUpdatedState(scanning)
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()

    MessageScreen(
        icon = R.drawable.ic_music_note,
        title = stringResource(R.string.no_songs_title),
        text = stringResource(R.string.no_songs_text),
        action = stringResource(R.string.rescan),
        onAction = {
            onRescan()
            // A rescan usually takes milliseconds, so the arrow always makes at least one turn,
            // then keeps turning while the rescan is still running.
            if (!reduceMotion && !rotation.isRunning) {
                scope.launch {
                    do {
                        rotation.snapTo(0f)
                        rotation.animateTo(360f, tween(TurnMillis, easing = LinearEasing))
                    } while (stillScanning)
                    rotation.snapTo(0f)
                }
            }
        },
        actionIcon = {
            Icon(
                painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation.value },
            )
        },
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun NoSongsScreenPreview() {
    GusleTheme {
        NoSongsScreen(scanning = false, onRescan = {})
    }
}
