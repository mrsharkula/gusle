package com.sharkula.gusle.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.IconCircle
import com.sharkula.gusle.ui.nowplaying.JumpStep
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * Settings → Jump by: every [JumpStep] ([StepChooserScreen]), each with its own icon in a circle,
 * the very icon the seek screen's button has. Choosing one, the seek screen's and the tile's − and
 * + buttons jump by it from then on, which the line under the list says.
 */
@Composable
fun JumpStepScreen(current: JumpStep, onChoose: (JumpStep) -> Unit) {
    val colors = MaterialTheme.colorScheme
    StepChooserScreen(
        title = stringResource(R.string.jump_by),
        steps = JumpStep.entries,
        current = current,
        default = JumpStep.Default,
        id = { it.id },
        seconds = { it.seconds },
        note = stringResource(R.string.jump_by_note),
        icon = { step ->
            IconCircle(
                icon = step.backIcon,
                background = colors.secondaryContainer,
                tint = colors.onSecondaryContainer,
            )
        },
        onChoose = onChoose,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun JumpStepScreenPreview() {
    GusleTheme {
        JumpStepScreen(current = JumpStep.Default, onChoose = {})
    }
}
