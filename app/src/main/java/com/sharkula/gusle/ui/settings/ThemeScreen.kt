package com.sharkula.gusle.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.components.RowCircleSize
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.theme.AppTheme
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * Settings → Theme: every [AppTheme], each with a circle in its own main color, the [current] one
 * selected. Tapping one chooses it, and every screen takes its colors at once, this one too, which
 * stays open. While a song with a cover plays, the screens are in the song's colors instead, which
 * the line under the list says.
 */
@Composable
fun ThemeScreen(current: AppTheme, onChoose: (AppTheme) -> Unit) {
    // The circles need every theme's colors. Settings has them made as it opens, so they're
    // usually ready; if not, they're made off the main thread and the circles come in after.
    val colorsMade by produceState(AppTheme.entries.all { it.hasColors }) {
        if (!value) {
            AppTheme.makeAllColors()
            value = true
        }
    }

    ListScreen(title = stringResource(R.string.theme)) { transformationSpec ->
        items(AppTheme.entries, key = { it.id }) { theme ->
            // Read out as the theme's name, "Default" for walnut, and whether it's selected.
            RadioButton(
                selected = theme == current,
                onSelect = { onChoose(theme) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                icon = {
                    Box(
                        Modifier.size(RowCircleSize)
                            .background(if (colorsMade) theme.colors.primary else Color.Transparent, CircleShape),
                    )
                },
                label = { Text(stringResource(theme.label)) },
                secondaryLabel = if (theme == AppTheme.Default) {
                    { Text(stringResource(R.string.default_choice)) }
                } else {
                    null
                },
            )
        }
        item(key = "note") {
            Text(
                stringResource(R.string.theme_note),
                modifier = Modifier.edgeTransformation(this, transformationSpec).fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun ThemeScreenPreview() {
    GusleTheme {
        ThemeScreen(current = AppTheme.Default, onChoose = {})
    }
}
