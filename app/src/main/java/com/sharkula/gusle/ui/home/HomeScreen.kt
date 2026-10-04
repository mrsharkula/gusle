package com.sharkula.gusle.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
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
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val colors = MaterialTheme.colorScheme
    val laterColors = ButtonDefaults.filledTonalButtonColors()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onSettings,
                buttonSize = EdgeButtonSize.Small,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(stringResource(R.string.settings))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.home_title))
                }
            }
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
}

/**
 * Leading 32dp circle holding an 18dp icon. Song rows show their cover in the same 32dp
 * circle, so every list row in the app lines up the same way.
 */
@Composable
private fun IconCircle(
    @DrawableRes icon: Int,
    background: Color,
    tint: Color = LocalContentColor.current,
) {
    Box(
        modifier = Modifier.size(32.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    GusleTheme {
        HomeScreen(onAllSongs = {}, onSettings = {})
    }
}
