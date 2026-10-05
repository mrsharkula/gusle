package com.sharkula.gusle.ui.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.ui.components.ListScreen
import com.sharkula.gusle.ui.theme.GusleTheme

/** Every song on the watch, A–Z. The song rows arrive with MediaStore loading. */
@Composable
fun AllSongsScreen() {
    ListScreen(title = stringResource(R.string.all_songs)) {}
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AllSongsScreenPreview() {
    GusleTheme {
        AllSongsScreen()
    }
}
