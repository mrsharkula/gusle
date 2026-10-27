package com.sharkula.gusle.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.nowplaying.CrownStep
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * Settings → Crown step: every [CrownStep] ([StepChooserScreen]), without icons, as the steps have
 * none of their own. Choosing one, a step of the crown on the seek screen moves by it from then
 * on; the line under the list says so, and that quick turns move further.
 */
@Composable
fun CrownStepScreen(current: CrownStep, onChoose: (CrownStep) -> Unit) {
    StepChooserScreen(
        title = stringResource(R.string.crown_step),
        steps = CrownStep.entries,
        current = current,
        default = CrownStep.Default,
        id = { it.id },
        seconds = { it.seconds },
        note = stringResource(R.string.crown_step_note),
        onChoose = onChoose,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun CrownStepScreenPreview() {
    GusleTheme {
        CrownStepScreen(current = CrownStep.Default, onChoose = {})
    }
}
