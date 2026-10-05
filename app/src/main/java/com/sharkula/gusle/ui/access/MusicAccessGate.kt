package com.sharkula.gusle.ui.access

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.MusicPermission
import com.sharkula.gusle.data.hasMusicAccess
import com.sharkula.gusle.ui.components.MessageScreen
import com.sharkula.gusle.ui.theme.GusleTheme

private enum class MusicAccess {
    Granted,

    /** Not granted, and the watch will still show its prompt. */
    CanAsk,

    /** Refused for good: the watch won't show its prompt again, only its settings can turn it on. */
    Refused,
}

/**
 * Shows [content] once Gusle may read the songs on the watch. Until then it asks with "Play your
 * music", whose Allow opens the watch's own prompt. Once the watch won't show that prompt
 * anymore, "Music access is off" leads to Gusle's page in the watch's settings instead.
 */
@Composable
fun MusicAccessGate(content: @Composable () -> Unit) {
    val activity = checkNotNull(LocalActivity.current)
    var access by remember { mutableStateOf(activity.musicAccess()) }
    // Set while the prompt or the settings page opens, so a double tap can't open it twice.
    var opening by remember { mutableStateOf(false) }

    fun recheck() {
        opening = false
        access = activity.musicAccess()
    }

    val prompt = rememberLauncherForActivityResult(RequestPermission()) { recheck() }
    // Access may have been turned on in the watch's settings while Gusle was in the background.
    LifecycleResumeEffect(activity) {
        recheck()
        onPauseOrDispose {}
    }

    when (access) {
        MusicAccess.Granted -> content()
        MusicAccess.CanAsk -> MusicPermissionScreen(
            onAllow = {
                if (!opening) {
                    opening = true
                    prompt.launch(MusicPermission)
                }
            },
        )
        MusicAccess.Refused -> MusicAccessOffScreen(
            onOpenSettings = { if (!opening) opening = activity.openAppSettings() },
        )
    }
}

// Android only says whether to explain before asking again: yes after a first refusal, no both
// before the first prompt and after the last one. To tell those two apart, Gusle remembers that
// it has seen a refusal.
private const val AccessPrefs = "music_access"
private const val RefusedOnce = "refused_once"

private fun Activity.musicAccess(): MusicAccess {
    val prefs = getSharedPreferences(AccessPrefs, Context.MODE_PRIVATE)
    if (hasMusicAccess()) {
        // However it was turned on: if it's ever taken away again (Android does that to apps
        // left unused for months), the prompt works again too.
        prefs.edit().remove(RefusedOnce).apply()
        return MusicAccess.Granted
    }
    if (shouldShowRequestPermissionRationale(MusicPermission)) {
        prefs.edit().putBoolean(RefusedOnce, true).apply()
        return MusicAccess.CanAsk
    }
    // Not refused before: never asked, or the prompt was swiped away without an answer.
    return if (prefs.getBoolean(RefusedOnce, false)) MusicAccess.Refused else MusicAccess.CanAsk
}

/**
 * Opens Gusle's page in the watch's settings, or the settings themselves if there's no such
 * page. Returns whether either opened.
 */
private fun Context.openAppSettings(): Boolean {
    val appPage = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    return listOf(appPage, Intent(Settings.ACTION_SETTINGS)).any { intent ->
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

@Composable
private fun MusicPermissionScreen(onAllow: () -> Unit) {
    MessageScreen(
        icon = R.drawable.ic_music_note,
        title = stringResource(R.string.permission_title),
        text = stringResource(R.string.permission_text),
        action = stringResource(R.string.allow),
        onAction = onAllow,
        iconBackground = MaterialTheme.colorScheme.primary,
        iconTint = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun MusicAccessOffScreen(onOpenSettings: () -> Unit) {
    MessageScreen(
        icon = R.drawable.ic_music_off,
        title = stringResource(R.string.access_off_title),
        text = stringResource(R.string.access_off_text),
        action = stringResource(R.string.open_settings),
        onAction = onOpenSettings,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun MusicPermissionScreenPreview() {
    GusleTheme {
        MusicPermissionScreen(onAllow = {})
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun MusicAccessOffScreenPreview() {
    GusleTheme {
        MusicAccessOffScreen(onOpenSettings = {})
    }
}
