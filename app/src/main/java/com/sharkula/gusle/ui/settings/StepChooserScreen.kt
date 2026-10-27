package com.sharkula.gusle.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.components.edgeTransformation

/**
 * A Settings chooser of a step in seconds, Jump by's ([JumpStepScreen]) and Crown step's
 * ([CrownStepScreen]): every one of [steps] as a radio row, "10 seconds", with "Default" under the
 * [default] one and the [current] one selected, and a [note] under the list saying what the step is
 * for. Each row has its step's [icon] where the steps have icons of their own. Tapping a row
 * chooses its step ([onChoose]); the screen stays open.
 */
@Composable
fun <T : Any> StepChooserScreen(
    title: String,
    steps: List<T>,
    current: T,
    default: T,
    /** What a step is saved as: the rows' keys. */
    id: (T) -> String,
    seconds: (T) -> Int,
    note: String,
    icon: (@Composable (T) -> Unit)? = null,
    onChoose: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ListScreen(title = title) { transformationSpec ->
        items(steps, key = id) { step ->
            // Read out as the step, "10 seconds", "Default" under the default one, and whether it's
            // selected.
            RadioButton(
                selected = step == current,
                onSelect = { onChoose(step) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                icon = icon?.let { draw -> { draw(step) } },
                label = { Text(pluralStringResource(R.plurals.seconds, seconds(step), seconds(step))) },
                secondaryLabel = if (step == default) {
                    { Text(stringResource(R.string.default_choice)) }
                } else {
                    null
                },
            )
        }
        item(key = "note") {
            Text(
                note,
                modifier = Modifier.edgeTransformation(this, transformationSpec).fillMaxWidth()
                    .padding(top = 4.dp, start = 8.dp, end = 8.dp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
