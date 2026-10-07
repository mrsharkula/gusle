package com.sharkula.gusle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.AppScaffold
import com.sharkula.gusle.ui.GusleNavHost
import com.sharkula.gusle.ui.NowPlayingRequest
import com.sharkula.gusle.ui.access.MusicAccessGate
import com.sharkula.gusle.ui.songs.SongsGate
import com.sharkula.gusle.ui.theme.GusleTheme

/**
 * The manifest's alias of MainActivity that opens Now playing. It isn't exported, so only Gusle's
 * own intents reach it: other apps can open Gusle, but not make it connect to the player.
 */
private const val OpenNowPlaying = "com.sharkula.gusle.OpenNowPlaying"

/** Opens Gusle on Now playing, on top of the screen it's on; the watch's media controls use it. */
fun Context.nowPlayingIntent(): Intent = Intent().setClassName(this, OpenNowPlaying)

class MainActivity : ComponentActivity() {
    private val openNowPlaying = NowPlayingRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not when the activity is created again, e.g. after a font size change: it was asked then.
        if (savedInstanceState == null) takeRequest(intent)
        val library = musicLibrary
        setContent {
            GusleTheme {
                AppScaffold {
                    MusicAccessGate {
                        SongsGate(library) { songs ->
                            GusleNavHost(songs, openNowPlaying)
                        }
                    }
                }
            }
        }
    }

    // Launched again while it's open (it's single-top), e.g. from the media controls.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeRequest(intent)
    }

    override fun onStop() {
        super.onStop()
        // A request the navigation didn't take while Gusle was shown, e.g. because music access is
        // off, doesn't hold for the next time.
        openNowPlaying.pending = false
    }

    private fun takeRequest(intent: Intent) {
        // Opened from the recent apps, the intent that started the task comes again; that's not a
        // tap on the media controls.
        val fromRecents = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (intent.component?.className == OpenNowPlaying && !fromRecents) {
            openNowPlaying.pending = true
        }
    }
}
