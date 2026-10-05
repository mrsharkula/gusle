package com.sharkula.gusle.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.theme.GusleTheme

private data class Source(@DrawableRes val icon: Int, @StringRes val label: Int)

// Shown on Home but not available until after v1.
private val laterSources = listOf(
    Source(R.drawable.ic_queue_music, R.string.playlists),
    Source(R.drawable.ic_album, R.string.albums),
    Source(R.drawable.ic_person, R.string.artists),
    Source(R.drawable.ic_folder, R.string.folders),
)

@Composable
fun HomeScreen(
    onAllSongs: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val laterColors = ButtonDefaults.filledTonalButtonColors()

    ListScreen(
        title = stringResource(R.string.home_title),
        edgeButton = {
            EdgeButton(
                onClick = onSettings,
                buttonSize = EdgeButtonSize.Small,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(stringResource(R.string.settings))
            }
        },
    ) { transformationSpec ->
        item {
            Button(
                onClick = onAllSongs,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                colors = ButtonDefaults.filledVariantButtonColors(),
                icon = {
                    IconCircle(
                        icon = R.drawable.ic_music_note,
                        background = colors.primary,
                        tint = colors.onPrimary,
                    )
                },
                label = { Text(stringResource(R.string.all_songs)) },
            )
        }
        items(laterSources, key = { it.label }) { source ->
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                colors = laterColors,
                icon = {
                    // Disabled: the circle uses the button's own disabled container color;
                    // the icon keeps its disabled content color.
                    IconCircle(
                        icon = source.icon,
                        background = laterColors.disabledContainerColor,
                    )
                },
                label = { Text(stringResource(source.label)) },
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    GusleTheme {
        HomeScreen(onAllSongs = {}, onSettings = {})
    }
}
