package com.sharkula.gusle.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.components.edgeTransformation
import com.sharkula.gusle.ui.nowplaying.JumpStep
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * Settings → Jump by: every [JumpStep], each with its own icon in a circle, the [current] one
 * selected. Tapping one chooses it, and the seek screen's and the tile's − and + buttons jump by it
 * from then on; this screen stays open. The line under the list says which buttons it's for.
 */
@Composable
fun JumpStepScreen(current: JumpStep, onChoose: (JumpStep) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ListScreen(title = stringResource(R.string.jump_by)) { transformationSpec ->
        items(JumpStep.entries, key = { it.id }) { step ->
            // Read out as the step, "10 seconds", "Default" for ten, and whether it's selected.
            RadioButton(
                selected = step == current,
                onSelect = { onChoose(step) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                icon = {
                    // The very icon the seek screen's button has.
                    IconCircle(
                        icon = step.backIcon,
                        background = colors.secondaryContainer,
                        tint = colors.onSecondaryContainer,
                    )
                },
                label = { Text(pluralStringResource(R.plurals.seconds, step.seconds, step.seconds)) },
                secondaryLabel = if (step == JumpStep.Default) {
                    { Text(stringResource(R.string.default_choice)) }
                } else {
                    null
                },
            )
        }
        item(key = "note") {
            Text(
                stringResource(R.string.jump_by_note),
                modifier = Modifier.edgeTransformation(this, transformationSpec).fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun JumpStepScreenPreview() {
    GusleTheme {
        JumpStepScreen(current = JumpStep.Default, onChoose = {})
    }
}
