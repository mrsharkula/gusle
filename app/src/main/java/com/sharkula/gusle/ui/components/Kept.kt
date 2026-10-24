package com.sharkula.gusle.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * [value], or while it's null, the last value it had that wasn't: e.g. an artist whose songs are all
 * gone, until their screen has closed.
 */
@Composable
fun <T : Any> lastNotNull(value: T?): T? = keptWhile(held = value == null, value)

/** [value], or while [held], the last value it had before. */
@Composable
fun <T> keptWhile(held: Boolean, value: T): T {
    val kept = remember { Kept(value) }
    SideEffect { if (!held) kept.value = value }
    return if (held) kept.value else value
}

private class Kept<T>(var value: T)
