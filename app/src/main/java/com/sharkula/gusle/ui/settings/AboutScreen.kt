package com.sharkula.gusle.ui.settings

import android.content.pm.PackageManager.PackageInfoFlags
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.iconThemeChoice
import com.sharkula.gusle.ui.components.LauncherIconPicture
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.theme.AppTheme
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.LocalAppTheme
import com.sharkula.gusle.ui.theme.launcherIconTheme

/** Gusle's icon, name and version, and what it does; opened from Settings. */
@Composable
fun AboutScreen() {
    // The icon as the launcher shows it, or will once Gusle leaves the screen (LauncherIcon).
    val follows = LocalContext.current.iconThemeChoice.current == true
    AboutScreen(version = rememberVersionName(), iconTheme = launcherIconTheme(LocalAppTheme.current, follows))
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
private fun AboutScreen(version: String, iconTheme: AppTheme) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    // A list, so it scrolls when a large font doesn't fit.
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                LauncherIconPicture(
                    iconTheme,
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

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AboutScreenPreview() {
    GusleTheme {
        AboutScreen(version = "1.0", iconTheme = AppTheme.Default)
    }
}
