package com.sharkula.gusle.ui.songs

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows [content] with the songs on the watch, or "No songs yet" if there are none. Only shown
 * once access is on.
 */
@Composable
fun SongsGate(library: MusicLibrary, content: @Composable (songs: List<Song>) -> Unit) {
    // Read whenever Gusle comes back to the screen, so music sent or deleted meanwhile shows up.
    LifecycleResumeEffect(library) {
        library.refresh()
        onPauseOrDispose {}
    }
    val songs by library.songs.collectAsStateWithLifecycle()

    when (val state = songs) {
        // Reading takes milliseconds, too short for anything but the black screen. No access
        // doesn't last either: access is on by now, and the refresh above reads again.
        Songs.Loading, Songs.NoAccess -> Unit
        Songs.Failed -> RescanScreen(R.string.read_failed_title, R.string.read_failed_text, library)
        is Songs.Loaded ->
            if (state.list.isEmpty()) {
                RescanScreen(R.string.no_songs_title, R.string.no_songs_text, library)
            } else {
                content(state.list)
            }
    }
}

/** Says why there are no songs to show, with Rescan in the edge button. */
@Composable
private fun RescanScreen(@StringRes title: Int, @StringRes text: Int, library: MusicLibrary) {
    // Collected here rather than in the gate, so a read doesn't recompose the rest of the app.
    val scanning by library.scanning.collectAsStateWithLifecycle()
    RescanScreen(stringResource(title), stringResource(text), scanning, onRescan = library::refresh)
}

private const val TurnMillis = 1100

@Composable
private fun RescanScreen(title: String, text: String, scanning: Boolean, onRescan: () -> Unit) {
    // From the tap until the rescan is done, but at least one turn of the arrow: a rescan usually
    // takes milliseconds and would show nothing. The label says so too, for Reduce motion.
    var busy by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val stillScanning by rememberUpdatedState(scanning)
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()

    MessageScreen(
        icon = R.drawable.ic_music_note,
        title = title,
        text = text,
        action = stringResource(if (busy) R.string.scanning else R.string.rescan),
        onAction = {
            if (!busy) {
                busy = true
                onRescan()
                scope.launch {
                    do {
                        if (reduceMotion) {
                            delay(TurnMillis.toLong())
                        } else {
                            rotation.snapTo(0f)
                            rotation.animateTo(360f, tween(TurnMillis, easing = LinearEasing))
                        }
                    } while (stillScanning)
                    rotation.snapTo(0f)
                    busy = false
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
        RescanScreen(
            title = stringResource(R.string.no_songs_title),
            text = stringResource(R.string.no_songs_text),
            scanning = false,
            onRescan = {},
        )
    }
}
