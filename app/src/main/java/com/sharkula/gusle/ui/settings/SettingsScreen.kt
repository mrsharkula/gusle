package com.sharkula.gusle.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.sharkula.gusle.ui.nowplaying.CrownStep
import com.sharkula.gusle.ui.nowplaying.JumpStep
import com.sharkula.gusle.ui.nowplaying.LocalCrownStep
import com.sharkula.gusle.ui.nowplaying.LocalJumpStep
import com.sharkula.gusle.ui.songs.RescanState
import com.sharkula.gusle.ui.songs.rememberRescanState
import com.sharkula.gusle.ui.theme.AppTheme
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.LocalAppTheme

/**
 * Stop playing, while there's a song to stop; Theme, with the chosen one's name; Jump by, with the
 * step the − and + buttons jump by; Crown step, with how far a step of the crown moves on the seek
 * screen; Rescan music, with how many songs there are; and About, with Gusle's version. Opened
 * from Home.
 */
@Composable
fun SettingsScreen(
    songCount: Int,
    /** Whether the player has a song, playing or paused, or one it comes back to. */
    hasSong: Boolean,
    /** That song's title; null when it's no longer among the songs (deleted while playing). */
    songTitle: String?,
    /** Stop playing was tapped: stop the song, forget it, and close Gusle. */
    onStopPlaying: () -> Unit,
    onTheme: () -> Unit,
    onJumpStep: () -> Unit,
    onCrownStep: () -> Unit,
    onAbout: () -> Unit,
) {
    val rescan = rememberRescanState(LocalContext.current.musicLibrary)
    // Makes every theme's colors now, in the background, for the Theme screen's circles and for
    // choosing one, so neither waits for them.
    LaunchedEffect(Unit) { AppTheme.makeAllColors() }
    SettingsScreen(
        songCount = songCount,
        hasSong = hasSong,
        songTitle = songTitle,
        onStopPlaying = onStopPlaying,
        themeName = LocalAppTheme.current.label,
        onTheme = onTheme,
        jumpStep = LocalJumpStep.current,
        onJumpStep = onJumpStep,
        crownStep = LocalCrownStep.current,
        onCrownStep = onCrownStep,
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
    hasSong: Boolean,
    songTitle: String?,
    onStopPlaying: () -> Unit,
    /** The chosen app theme's name; shown also while the screen is in a song's colors. */
    @StringRes themeName: Int,
    onTheme: () -> Unit,
    /** The step the − and + buttons jump by, for its row. */
    jumpStep: JumpStep,
    onJumpStep: () -> Unit,
    /** How far a step of the crown moves on the seek screen, for its row. */
    crownStep: CrownStep,
    onCrownStep: () -> Unit,
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
        // The way out of a song Gusle would otherwise come back to, and whose colors it would keep.
        if (hasSong) {
            item(key = "stop") {
                ListButton(
                    onClick = onStopPlaying,
                    transformationSpec = transformationSpec,
                    colors = rowColors,
                    icon = {
                        IconCircle(
                            icon = R.drawable.ic_stop,
                            background = colors.secondaryContainer,
                            tint = colors.onSecondaryContainer,
                        )
                    },
                    label = { Text(stringResource(R.string.stop_playing)) },
                    secondaryLabel = songTitle?.let { title ->
                        { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                )
            }
        }
        item(key = "theme") {
            ListButton(
                onClick = onTheme,
                transformationSpec = transformationSpec,
                colors = rowColors,
                icon = {
                    IconCircle(
                        icon = R.drawable.ic_palette,
                        background = colors.secondaryContainer,
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.theme)) },
                secondaryLabel = { Text(stringResource(themeName)) },
            )
        }
        item(key = "jump_step") {
            ListButton(
                onClick = onJumpStep,
                transformationSpec = transformationSpec,
                colors = rowColors,
                icon = {
                    // The step's own icon, with its number: the very button the seek screen has.
                    IconCircle(
                        icon = jumpStep.backIcon,
                        background = colors.secondaryContainer,
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.jump_by)) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.seconds, jumpStep.seconds, jumpStep.seconds)) },
            )
        }
        item(key = "crown_step") {
            ListButton(
                onClick = onCrownStep,
                transformationSpec = transformationSpec,
                colors = rowColors,
                icon = {
                    // The crown as the seek screen's hint draws it.
                    IconCircle(
                        icon = R.drawable.ic_crown,
                        background = colors.secondaryContainer,
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(stringResource(R.string.crown_step)) },
                secondaryLabel = { Text(pluralStringResource(R.plurals.seconds, crownStep.seconds, crownStep.seconds)) },
            )
        }
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
            hasSong = false,
            songTitle = null,
            onStopPlaying = {},
            themeName = AppTheme.Default.label,
            onTheme = {},
            jumpStep = JumpStep.Default,
            onJumpStep = {},
            crownStep = CrownStep.Default,
            onCrownStep = {},
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
            hasSong = false,
            songTitle = null,
            onStopPlaying = {},
            themeName = AppTheme.Default.label,
            onTheme = {},
            jumpStep = JumpStep.Default,
            onJumpStep = {},
            crownStep = CrownStep.Default,
            onCrownStep = {},
            scanning = true,
            iconRotation = { 0f },
            version = "1.0",
            onRescan = {},
            onAbout = {},
        )
    }
}

/** With a song Gusle would come back to. */
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SettingsScreenPlayingPreview() {
    GusleTheme {
        SettingsScreen(
            songCount = 51,
            hasSong = true,
            songTitle = "River at Dawn",
            onStopPlaying = {},
            themeName = AppTheme.Default.label,
            onTheme = {},
            jumpStep = JumpStep.Default,
            onJumpStep = {},
            crownStep = CrownStep.Default,
            onCrownStep = {},
            scanning = false,
            iconRotation = { 0f },
            version = "1.0",
            onRescan = {},
            onAbout = {},
        )
    }
}
