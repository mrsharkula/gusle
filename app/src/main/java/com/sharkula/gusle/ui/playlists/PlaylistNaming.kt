package com.sharkula.gusle.ui.playlists

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.sharkula.gusle.R

private const val Tag = "PlaylistNaming"

/** The text input's one field. */
private const val NameKey = "playlist_name"

private val LineBreaks = Regex("[\\r\\n]+")

/**
 * Naming a playlist with Wear's own text input: "Playlist name" on top, and the keyboard or voice
 * under it ([ask]). What's typed or said reaches the screen that asked ([rememberPlaylistNaming]).
 * Nothing is suggested for a new one: the name is the user's. Renaming one offers its name now, which
 * a tap keeps. The keyboard opens empty and can't send an empty name; a swipe right cancels.
 */
@Stable
class PlaylistNaming internal constructor(private val label: String) {
    /** Starts Wear's text input; set as the screen is composed. */
    internal var launcher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null

    /** While Wear's text input is open: a second tap, e.g. a double tap, doesn't open another. */
    private var asking = false

    /**
     * Opens Wear's text input, unless it's open already, with [current], a renamed playlist's name
     * now, as its one ready answer (a chip under the keyboard and voice); none for a new playlist.
     */
    fun ask(current: String? = null) {
        val launcher = launcher ?: return
        if (asking) return
        val input = RemoteInput.Builder(NameKey)
            .setLabel(label)
            .setAllowFreeFormInput(true)
            .apply { if (current != null) setChoices(arrayOf<CharSequence>(current)) }
            // Only Wear's own buttons, the keyboard and voice, as tried on the watch.
            .wearableExtender { setEmojisAllowed(false) }
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        try {
            launcher.launch(intent)
            asking = true
        } catch (e: ActivityNotFoundException) {
            Log.w(Tag, "No text input on this watch", e)
        }
    }

    /** Wear's text input closed, with a name or without. */
    internal fun answered() {
        asking = false
    }
}

/**
 * Naming a playlist ([PlaylistNaming.ask]), new or renamed: [onNamed] gets the name, tidied
 * ([tidyName]). Nothing happens when the text input is cancelled, or the name is only spaces. Kept
 * with the screen's saved state, so the name still arrives after Gusle is recreated meanwhile.
 */
@Composable
fun rememberPlaylistNaming(onNamed: (name: String) -> Unit): PlaylistNaming {
    val label = stringResource(R.string.playlist_name)
    val named by rememberUpdatedState(onNamed)
    val naming = remember(label) { PlaylistNaming(label) }
    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        naming.answered()
        nameIn(result)?.let { named(it) }
    }
    SideEffect { naming.launcher = launcher }
    return naming
}

/** The name typed or said in [result], tidied; null when it was cancelled, or is blank. */
private fun nameIn(result: ActivityResult): String? {
    if (result.resultCode != Activity.RESULT_OK) return null
    val data = result.data ?: return null
    return RemoteInput.getResultsFromIntent(data)?.getCharSequence(NameKey)?.let { tidyName(it.toString()) }
}

/** [text] as a playlist's name: on one line, without spaces around it; null if nothing's left. */
internal fun tidyName(text: String): String? = text.replace(LineBreaks, " ").trim().ifEmpty { null }
