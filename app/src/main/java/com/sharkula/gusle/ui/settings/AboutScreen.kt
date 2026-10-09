package com.sharkula.gusle.ui.settings

import android.content.pm.PackageManager.PackageInfoFlags
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.theme.GusleTheme

/** Gusle's icon, name and version, and what it does; opened from Settings. */
@Composable
fun AboutScreen() {
    AboutScreen(version = rememberVersionName())
}

/** Gusle's version name as installed, e.g. "1.0". */
@Composable
fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        context.packageManager.getPackageInfo(context.packageName, PackageInfoFlags.of(0)).versionName.orEmpty()
    }
}

@Composable
private fun AboutScreen(version: String) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    // A list, so it scrolls when a large font doesn't fit.
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                LauncherIcon(
                    size = 52.dp,
                    modifier = Modifier.edgeTransformation(this, transformationSpec).padding(top = 16.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier
                        .edgeTransformation(this, transformationSpec)
                        .padding(top = 8.dp)
                        .semantics { heading() },
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item {
                Text(
                    stringResource(R.string.version, version),
                    modifier = Modifier.edgeTransformation(this, transformationSpec),
                    color = secondaryColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Text(
                    stringResource(R.string.about_text),
                    modifier = Modifier
                        .edgeTransformation(this, transformationSpec)
                        .fillMaxWidth(0.66f)
                        .padding(top = 4.dp),
                    color = secondaryColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * An adaptive icon's layers are half as big again as the icon: launchers show their middle two
 * thirds, cut to the launcher's shape.
 */
private const val AdaptiveIconLayerScale = 1.5f

/**
 * Gusle's launcher icon, [size] across, as the watch's launcher shows it: the adaptive icon's
 * foreground on its background color, cut to a circle. painterResource can't draw the adaptive
 * icon itself, so this draws its layers the way a launcher does. It's only a picture; the name
 * below it says whose icon it is.
 */
@Composable
private fun LauncherIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * AdaptiveIconLayerScale),
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AboutScreenPreview() {
    GusleTheme {
        AboutScreen(version = "1.0")
    }
}
