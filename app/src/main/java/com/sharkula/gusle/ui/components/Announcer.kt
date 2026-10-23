package com.sharkula.gusle.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * A message waits this long for the screen to settle: a dialog closing, or Gusle coming back from
 * another app's screen. Screen readers announce those first, and a message that changes meanwhile,
 * while that window still has their attention, isn't read out at all (seen with TalkBack on Wear).
 */
private const val SettleMs = 1_000L

/** A message stays this long; then the region is empty again until the next one. */
private const val ClearAfterMs = 4_000L

/**
 * A message not read out this soon after it was said goes unsaid, e.g. when Gusle left the screen
 * meanwhile: hearing "Deleted" the next time Gusle opens would only puzzle.
 */
private const val FreshForMs = 3_000L

/**
 * Tells screen readers what nothing on the screen says, e.g. "Deleted" once a song is gone and its
 * screen has closed. The app keeps one, so a message outlives the screen that asked for it;
 * MainActivity shows it over every screen ([Announcement]). Use it from the main thread.
 */
class Announcer {
    /** A message to say, said at [saidAtMs] ([SystemClock.elapsedRealtime]); each is new, even in the same words. */
    internal class Message(val text: String, val saidAtMs: Long = SystemClock.elapsedRealtime())

    /** Said, and not yet taken by [Announcement]; so a screen recreated later doesn't say it again. */
    internal var pending: Message? by mutableStateOf(null)
        private set

    /** Has screen readers read out [text], politely: after what they're saying. */
    fun say(text: String) {
        pending = Message(text)
    }

    internal fun take(message: Message) {
        if (pending === message) pending = null
    }
}

/**
 * Where [announcer]'s messages go: a polite live region that shows nothing. Screen readers read out
 * a change of its description, so it's emptied before each message, also the same words again, and
 * a while after.
 */
@Composable
fun Announcement(announcer: Announcer, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(announcer) {
        snapshotFlow { announcer.pending }.filterNotNull().collectLatest { message ->
            announcer.take(message)
            shown = ""
            delay(SettleMs)
            if (SystemClock.elapsedRealtime() - message.saidAtMs > FreshForMs) return@collectLatest
            shown = message.text
            delay(ClearAfterMs)
            shown = ""
        }
    }
    Box(
        modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (shown.isNotEmpty()) contentDescription = shown
            },
    )
}
