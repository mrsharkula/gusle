package com.sharkula.gusle.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.LocalReduceMotion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the watch's screen is on, and then each change. It's off both when it's dark and when
 * the watch dims it to its always-on display. In the always-on display Gusle stays on the screen,
 * dimmed by the watch, and its activity stays resumed, so its lifecycle doesn't tell.
 */
fun Context.screenOn(): Flow<Boolean> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent.action == Intent.ACTION_SCREEN_ON)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
    }
    ContextCompat.registerReceiver(this@screenOn, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    // Read after registering, so no change in between is missed.
    trySend(getSystemService<PowerManager>()?.isInteractive ?: true)
    awaitClose { unregisterReceiver(receiver) }
}.conflate().distinctUntilChanged()

/**
 * Whether Gusle is on the screen and the screen is on: what anything that ticks or moves waits
 * for, so nothing redraws in the background or in the always-on display.
 */
@Composable
fun rememberScreenShown(): State<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    return produceState(initialValue = false, lifecycle, context) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                context.screenOn().collect { value = it }
            } finally {
                value = false
            }
        }
    }
}

/**
 * Whether anything may move frame by frame: only while Gusle is [shown] (see [rememberScreenShown])
 * and neither Wear's Reduce motion nor Android's Remove animations is on. Otherwise everything
 * stands still in its end state.
 */
@Composable
fun rememberMayAnimate(shown: State<Boolean> = rememberScreenShown()): Boolean {
    val animationsOff by rememberAnimationsOff()
    return shown.value && !LocalReduceMotion.current && !animationsOff
}

/**
 * Whether Android's animations are off: Remove animations in the accessibility settings, or the
 * animation scale at 0. Wear's own Reduce motion is a separate setting, LocalReduceMotion.
 */
@Composable
private fun rememberAnimationsOff(): State<Boolean> = produceState(initialValue = false) {
    // Compose's own animations already jump then; this is for the ones Gusle runs frame by frame.
    snapshotFlow { coroutineContext[MotionDurationScale]?.scaleFactor == 0f }.collect { value = it }
}

