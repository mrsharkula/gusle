package com.sharkula.gusle.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.musicLibrary
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.components.ListButton
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.songs.RescanState
import com.sharkula.gusle.ui.songs.rememberRescanState
import com.sharkula.gusle.ui.theme.GusleTheme

/** Rescan music, with how many songs there are, and About, with Gusle's version; opened from Home. */
@Composable
fun SettingsScreen(songCount: Int, onAbout: () -> Unit) {
    val rescan = rememberRescanState(LocalContext.current.musicLibrary)
    SettingsScreen(
        songCount = songCount,
        scanning = rescan.busy,
        iconRotation = { rescan.rotation },
        version = rememberVersionName(),
        onRescan = rescan::rescan,
        onAbout = onAbout,
    )
}

@Composable
private fun SettingsScreen(
    /** The songs on the watch, as last read. */
    songCount: Int,
    /** From the Rescan tap until it's done; see [RescanState]. */
    scanning: Boolean,
    /** The refresh icon's angle in degrees, read only while drawing. */
    iconRotation: () -> Float,
    version: String,
    onRescan: () -> Unit,
    onAbout: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rowColors = ButtonDefaults.filledTonalButtonColors()

    ListScreen(title = stringResource(R.string.settings)) { transformationSpec ->
        item(key = "rescan") {
            ListButton(
                onClick = onRescan,
                transformationSpec = transformationSpec,
                colors = rowColors,
                icon = {
                    IconCircle(
                        icon = R.drawable.ic_refresh,
                        background = colors.secondaryContainer,
                        // Turning the circle turns the arrow; the circle itself looks the same at any angle.
                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation() },
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.rescan_music)) },
                secondaryLabel = {
                    // Said in words too, for when the arrow may not turn, and read out as it changes,
                    // so a screen reader tells when the rescan is done and what it found.
                    Text(
                        if (scanning) {
                            stringResource(R.string.rescanning)
                        } else {
                            pluralStringResource(R.plurals.songs_found, songCount, songCount)
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
            )
        }
        item(key = "about") {
            ListButton(
                onClick = onAbout,
                transformationSpec = transformationSpec,
                colors = rowColors,
                icon = {
                    IconCircle(
                        icon = R.drawable.ic_info,
                        background = colors.secondaryContainer,
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.about)) },
                secondaryLabel = { Text(stringResource(R.string.version, version)) },
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    GusleTheme {
        SettingsScreen(
            songCount = 51,
            scanning = false,
            iconRotation = { 0f },
            version = "1.0",
            onRescan = {},
            onAbout = {},
        )
    }
}

/** While rescanning; the arrow turns only on the watch. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SettingsScreenScanningPreview() {
    GusleTheme {
        SettingsScreen(
            songCount = 51,
            scanning = true,
            iconRotation = { 0f },
            version = "1.0",
            onRescan = {},
            onAbout = {},
        )
    }
}
