package com.sharkula.gusle.ui.nowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.wear.compose.material3.MaterialTheme
import com.sharkula.gusle.R
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The volume the player shows along its bottom, which the crown and accessibility services change. */
@Stable
interface Volume {
    /** From silent (0) to loudest (1). It's state, so a drawing that reads it is redrawn on changes. */
    val fraction: Float

    /** How many notches there are from silent to loudest. */
    val notches: Int

    /** False when the volume can't be changed, e.g. when the watch fixes it. */
    val canChange: Boolean

    /** Turns it a notch up or down. Returns false when it's already at that end: nothing changes. */
    fun step(up: Boolean): Boolean

    /** Turns it to the notch nearest [fraction]. */
    fun set(fraction: Float)
}

/**
 * The watch's music volume, as the system has it. It follows changes made anywhere, e.g. with the
 * system's volume controls.
 *
 * Read and changed with AudioManager, not through the player: Media3 doesn't let a controller
 * change the volume of local playback (only with an option it's about to remove), and says to use
 * AudioManager instead.
 */
@Stable
private class MusicVolume(private val audio: AudioManager) : Volume {
    private val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private var level by mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC))

    override val fraction: Float get() = if (max > min) (level - min).toFloat() / (max - min) else 0f
    override val notches: Int get() = max - min
    override val canChange: Boolean = !audio.isVolumeFixed

    override fun step(up: Boolean): Boolean = moveTo { now -> now + if (up) 1 else -1 }

    override fun set(fraction: Float) {
        moveTo { min + (fraction * (max - min)).roundToInt() }
    }

    /** Takes the volume from the system again, after it says it changed. */
    fun update() {
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    private inline fun moveTo(target: (now: Int) -> Int): Boolean {
        // From the system's own level right now, so turning the crown quickly can't step (and
        // tick) past an end.
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = target(now).coerceIn(min, max)
        level = now
        if (!canChange || next == now) return false
        // No flags: no system volume panel or sound, as Gusle shows the volume itself.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        level = next
        return true
    }
}

/** The watch's music volume, for a screen that shows it. */
@Composable
fun rememberMusicVolume(): Volume {
    val context = LocalContext.current
    val volume = remember(context) { MusicVolume(checkNotNull(context.getSystemService<AudioManager>())) }
    LaunchedEffect(volume, context) {
        context.volumeChanges().collect { volume.update() }
    }
    return volume
}

/**
 * Each time the system says a volume changed, or where sound goes did (each output has a volume
 * of its own). Neither broadcast is in the SDK, but Android has long sent them, and Media3's own
 * volume tracking listens to the first.
 */
private fun Context.volumeChanges(): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction("android.media.VOLUME_CHANGED_ACTION")
        addAction("android.media.STREAM_DEVICES_CHANGED_ACTION")
    }
    ContextCompat.registerReceiver(this@volumeChanges, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { unregisterReceiver(receiver) }
}.conflate()

/**
 * Turns crown rotation into even steps: one for every [stepPx] turned, either way. Turning the
 * other way, or again after a pause, starts afresh, so each first step is as far as the rest.
 */
class CrownSteps(private val stepPx: Float) {
    private var turned = 0f
    private var lastTurnMs = 0L

    fun turn(px: Float, timeMs: Long, onStep: (up: Boolean) -> Unit) {
        if (px * turned < 0 || timeMs - lastTurnMs > CrownPauseMs) turned = 0f
        lastTurnMs = timeMs
        turned += px
        while (abs(turned) >= stepPx) {
            val up = turned > 0
            turned -= if (up) stepPx else -stepPx
            onStep(up)
        }
    }
}

/** A pause in turning the crown this long ends a turn. */
private const val CrownPauseMs = 400L

/** The volume arc runs along the bottom of the screen, from this angle (0° is 3 o'clock) to [VolumeEnd]. */
private const val VolumeStart = 65f
private const val VolumeEnd = 115f

/**
 * The [volume] along the bottom: a short arc that fills from its left end. The arc reads the volume
 * only while drawing. Accessibility services read it as a percentage and can change it. Its speaker
 * gave its place above the arc to the favorite heart (designs/notes/FAVORITES.md).
 */
@Composable
fun BoxScope.VolumeArc(volume: Volume) {
    val levelColor = MaterialTheme.colorScheme.onSurface
    Spacer(
        Modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val radius = 100.dp.toPx()
            val topLeft = size.center - Offset(radius, radius)
            val arcSize = Size(radius * 2, radius * 2)
            val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            val sweep = VolumeEnd - VolumeStart
            onDrawBehind {
                drawArc(VolumeTrack, VolumeStart, sweep, useCenter = false, topLeft, arcSize, style = stroke)
                val fraction = volume.fraction
                if (fraction > 0f) {
                    // From the left end toward the right, i.e. counterclockwise.
                    drawArc(levelColor, VolumeEnd, -sweep * fraction, useCenter = false, topLeft, arcSize, style = stroke)
                }
            }
        },
    )
    val description = stringResource(R.string.volume)
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val fraction = volume.fraction
    // Around the arc, below the heart above it, so accessibility services outline each on its own.
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = 205.dp)
            .size(width = 90.dp, height = 14.dp)
            .semantics {
                contentDescription = description
                stateDescription = percent.format(fraction)
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f, steps = (volume.notches - 1).coerceAtLeast(0))
                if (volume.canChange) {
                    setProgress { target ->
                        volume.set(target)
                        true
                    }
                }
            },
    )
}

private val VolumeTrack = Color.White.copy(alpha = 0.18f)
