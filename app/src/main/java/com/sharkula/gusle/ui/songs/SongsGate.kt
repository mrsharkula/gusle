package com.sharkula.gusle.ui.songs

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.MusicLibrary
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.ui.components.MessageScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * Shows [content] with the songs on the watch, or "No songs yet" if there are none. Only shown
 * once access is on.
 */
@Composable
fun SongsGate(library: MusicLibrary, content: @Composable (songs: Songs.Loaded) -> Unit) {
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
        is Songs.Loaded if state.list.isNotEmpty() -> content(state)
        // One screen for both, so a rescan that turns one into the other goes on turning.
        Songs.Failed, is Songs.Loaded -> RescanScreen(failed = state == Songs.Failed, library)
    }
}

/**
 * Says why there are no songs to show: there are none, or reading them [failed]. Rescan is in the
 * edge button.
 */
@Composable
private fun RescanScreen(failed: Boolean, library: MusicLibrary) {
    val rescan = rememberRescanState(library)
    RescanScreen(
        stringResource(if (failed) R.string.read_failed_title else R.string.no_songs_title),
        stringResource(if (failed) R.string.read_failed_text else R.string.no_songs_text),
        scanning = rescan.busy,
        iconRotation = { rescan.rotation },
        onRescan = rescan::rescan,
    )
}

@Composable
private fun RescanScreen(
    title: String,
    text: String,
    /** From the tap until the rescan is done; see [RescanState]. */
    scanning: Boolean,
    /** The refresh icon's angle in degrees, read only while drawing. */
    iconRotation: () -> Float,
    onRescan: () -> Unit,
) {
    MessageScreen(
        icon = R.drawable.ic_music_note,
        title = title,
        text = text,
        // Said in words too, for when the arrow may not turn.
        action = stringResource(if (scanning) R.string.scanning else R.string.rescan),
        onAction = onRescan,
        actionIcon = {
            Icon(
                painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = iconRotation() },
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
            iconRotation = { 0f },
            onRescan = {},
        )
    }
}
