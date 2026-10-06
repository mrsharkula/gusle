package com.sharkula.gusle.ui.nowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
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
