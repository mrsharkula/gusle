package com.sharkula.gusle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.AppScaffold
import com.sharkula.gusle.ui.GusleNavHost
import com.sharkula.gusle.ui.NowPlayingRequest
import com.sharkula.gusle.ui.access.MusicAccessGate
import com.sharkula.gusle.ui.ambient.AmbientMode
import com.sharkula.gusle.ui.ambient.AmbientState
import com.sharkula.gusle.ui.ambient.LocalAmbient
import com.sharkula.gusle.ui.components.Announcement
import com.sharkula.gusle.ui.nowplaying.LocalCrownStep
import com.sharkula.gusle.ui.nowplaying.LocalJumpStep
import com.sharkula.gusle.ui.songs.SongsGate
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

/**
 * The manifest's alias of MainActivity that opens Now playing. It isn't exported, so only Gusle's
 * own intents reach it: other apps can open Gusle, but not make it connect to the player. The tile
 * can't use it ([OpenNowPlayingExtra]).
 */
private const val OpenNowPlaying = "com.sharkula.gusle.OpenNowPlaying"

/** Opens Gusle on Now playing, on top of the screen it's on; the watch's media controls use it. */
fun Context.nowPlayingIntent(): Intent = Intent().setClassName(this, OpenNowPlaying)

/**
 * Asks MainActivity itself to open Now playing, as [nowPlayingIntent] does, for the tile: its taps
 * can only launch an exported activity, which any app can launch too. So it holds [NowPlayingKey],
 * a secret only Gusle knows, and without it MainActivity opens as from the launcher. Other apps
 * still can't make Gusle connect to the player.
 */
const val OpenNowPlayingExtra = "com.sharkula.gusle.OPEN_NOW_PLAYING"

class MainActivity : ComponentActivity() {
    private val openNowPlaying = NowPlayingRequest()

    /** Set when the navigation goes back, until the next touch; see [dispatchTouchEvent]. */
    private var wentBack = false

    /** Whether the watch shows Gusle's always-on display, which [ambientObserver] tells it. */
    private val ambient = AmbientState()

    /**
     * Tells the watch that Gusle draws its own always-on display, rather than the watch dimming
     * Gusle's screen or covering it with the watch face, and passes the watch's calls to [ambient].
     */
    private val ambientObserver = AmbientLifecycleObserver(this, ambient)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        // Not when the activity is created again, e.g. after a font size change: it was asked then.
        if (savedInstanceState == null) takeRequest(intent)
        // Reads the chosen theme and makes its colors off the main thread while the screen gets
        // ready: the first scheme made takes a while, as its code loads. Done once per process, so a
        // recreated activity has it at once.
        val themes = themeChoice
        lifecycleScope.launch { themes.load() }
        // Read alongside: for the seek screen's buttons and crown, and the Settings rows that name
        // the steps.
        val jumpSteps = jumpStepChoice
        lifecycleScope.launch { jumpSteps.load() }
        val crownSteps = crownStepChoice
        lifecycleScope.launch { crownSteps.load() }
        val library = musicLibrary
        val announcer = announcer
        setContent {
            // Nothing until all are known (they usually are by the first frame), so no screen, nor
            // the clock, shows in the default theme first, nor a restored Settings or seek screen the
            // default steps. Around the gates, so their screens follow them.
            val theme = themes.current
            val jumpStep = jumpSteps.current
            val crownStep = crownSteps.current
            if (theme != null && jumpStep != null && crownStep != null) {
                CompositionLocalProvider(
                    LocalAppTheme provides theme,
                    LocalJumpStep provides jumpStep,
                    LocalCrownStep provides crownStep,
                    LocalAmbient provides ambient,
                ) {
                    GusleTheme {
                        // Over every screen, the clock at the top too.
                        AmbientMode(ambient) {
                            AppScaffold {
                                MusicAccessGate {
                                    SongsGate(library) { songs ->
                                        GusleNavHost(songs, openNowPlaying, onWentBack = { wentBack = true })
                                    }
                                }
                                // Over the gates too: e.g. "Deleted" once the last song is gone.
                                Announcement(announcer, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // In case a call from the watch was missed while Gusle was off the screen.
        ambient.sync(ambientObserver.isAmbient)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // On the watch, the first touch after swiping back did nothing, as if the swipe were still
        // going on; the next one worked. A cancel first starts it from a clean slate. Checked on the
        // watch with the same 300 ms slides: without it, that first tap was lost every time.
        if (wentBack && event.actionMasked == MotionEvent.ACTION_DOWN) {
            wentBack = false
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        return super.dispatchTouchEvent(event)
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
        if (!fromRecents && (intent.component?.className == OpenNowPlaying || intent.hasNowPlayingKey())) {
            openNowPlaying.pending = true
        }
    }

    /** Whether this intent holds the key that only Gusle's tile sends ([OpenNowPlayingExtra]). */
    private fun Intent.hasNowPlayingKey(): Boolean {
        val sent = getStringExtra(OpenNowPlayingExtra) ?: return false
        return (application as GusleApp).nowPlayingKey.matches(sent)
    }
}
