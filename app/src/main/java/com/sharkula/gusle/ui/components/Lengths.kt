package com.sharkula.gusle.ui.components

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.sharkula.gusle.R

/** 3:31, or 1:02:05 from an hour on. Built by hand: it runs for every row that scrolls in. */
fun formatLength(ms: Long): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    return buildString {
        if (hours > 0) {
            append(hours).append(':')
            appendTwoDigits(seconds / 60 % 60)
        } else {
            append(seconds / 60)
        }
        append(':')
        appendTwoDigits(seconds % 60)
    }
}

private fun StringBuilder.appendTwoDigits(value: Long) {
    if (value < 10) append('0')
    append(value)
}

/** A part of a length, which [lengthInWords] names. */
enum class LengthUnit(@param:PluralsRes val words: Int) {
    Hours(R.plurals.hours),
    Minutes(R.plurals.minutes),
    Seconds(R.plurals.seconds),
}

/**
 * [ms] in words, as [formatLength] shows it, for screen readers, which may read "1:23" as a time
 * of day: e.g. "1 minute 23 seconds". The parts that are 0 are left out, unless all are ("0
 * seconds"). [word] names a count of a unit, e.g. "23 seconds".
 */
internal fun lengthInWords(ms: Long, word: (unit: LengthUnit, count: Int) -> String): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val parts = listOf(
        LengthUnit.Hours to seconds / 3600,
        LengthUnit.Minutes to seconds / 60 % 60,
        LengthUnit.Seconds to seconds % 60,
    ).filter { (_, count) -> count > 0 }
    if (parts.isEmpty()) return word(LengthUnit.Seconds, 0)
    return parts.joinToString(" ") { (unit, count) -> word(unit, count.toInt()) }
}

/** [lengthInWords] in the watch's language, for reading out lengths that change, e.g. a song's position. */
@Composable
fun rememberLengthInWords(): (ms: Long) -> String {
    val resources = LocalResources.current
    return remember(resources) {
        { ms -> lengthInWords(ms) { unit, count -> resources.getQuantityString(unit.words, count, count) } }
    }
}
