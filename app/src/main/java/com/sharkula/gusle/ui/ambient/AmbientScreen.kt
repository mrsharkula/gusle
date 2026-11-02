package com.sharkula.gusle.ui.ambient

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.tooling.preview.devices.WearDevices
import com.sharkula.gusle.R
import com.sharkula.gusle.data.Songs
import com.sharkula.gusle.musicLibrary
import com.sharkula.gusle.playingSong
import com.sharkula.gusle.playingSongHolder
import com.sharkula.gusle.ui.components.StillHeights
import com.sharkula.gusle.ui.components.formatLength
import com.sharkula.gusle.ui.covers.SongCover
import com.sharkula.gusle.ui.covers.playingSongColors
import com.sharkula.gusle.ui.nowplaying.DesignScale
import com.sharkula.gusle.ui.nowplaying.designText
import com.sharkula.gusle.ui.theme.AppTheme
import com.sharkula.gusle.ui.theme.GusleTheme
import com.sharkula.gusle.ui.theme.LocalAppTheme
import com.sharkula.gusle.ui.theme.seedColorScheme
import kotlin.math.roundToInt
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixFilter
import android.graphics.RenderEffect as AndroidRenderEffect

/** What the always-on display shows of a song. */
@Immutable
data class AmbientSong(
    val title: String,
    val artist: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    /** 0 when it isn't known: then there's no bar, nor time left. */
    val durationMs: Long,
)

/** The always-on display's few colors, all dim. */
@Immutable
data class AmbientColors(
    /** The time and the title. */
    val text: Color,
    /** The artist and the line under the bar. */
    val secondary: Color,
    /** The played part of the bar, and the equalizer: the one accent. */
    val accent: Color,
    /** The bar's track. */
    val track: Color,
)

/**
 * The always-on display's colors from [scheme], e.g. the playing song's: its text colors and its
 * primary, dimmed. A [lowBit] screen gets white, and no track.
 */
fun ambientColors(scheme: ColorScheme, lowBit: Boolean): AmbientColors =
    if (lowBit) {
        AmbientColors(text = Color.White, secondary = Color.White, accent = Color.White, track = Color.Transparent)
    } else {
        AmbientColors(
            text = scheme.onSurface.dimmed(0.78f),
            secondary = scheme.onSurfaceVariant.dimmed(0.58f),
            accent = scheme.primary.dimmed(0.62f),
            track = scheme.onSurfaceVariant.dimmed(0.16f),
        )
    }

/** This color at [brightness], darkened toward black as the design mixes it. */
private fun Color.dimmed(brightness: Float) = Color(red * brightness, green * brightness, blue * brightness)

/**
 * On a screen that can burn in, everything moves to the next of these at each update and each time
 * the watch goes into ambient: a few pixels, around where it sits otherwise (dp of the design).
 */
private val BurnInShifts = listOf(
    DpOffset(0.dp, 0.dp),
    DpOffset(2.5.dp, (-2).dp),
    DpOffset((-2).dp, 1.5.dp),
    DpOffset(1.5.dp, 2.5.dp),
    DpOffset((-2.5).dp, (-1).dp),
    DpOffset(2.dp, 1.dp),
    DpOffset((-1).dp, (-2.5).dp),
    DpOffset(0.5.dp, 2.dp),
)

/** Where everything is moved to at the [update]th of [AmbientState.updates]. */
internal fun burnInShift(update: Int): DpOffset = BurnInShifts[update.mod(BurnInShifts.size)]

/** The time left of a song [durationMs] long at [positionMs], in whole minutes, rounded; at least 1. */
internal fun minutesLeft(positionMs: Long, durationMs: Long): Int =
    ((durationMs - positionMs).coerceAtLeast(0) / 60_000.0).roundToInt().coerceAtLeast(1)

/**
 * The always-on display while the watch is in ambient ([state]): the player's song, or the one it
 * comes back to, in its colors. The time and the song's position are read anew at each of the
 * watch's updates, once a minute on the minute, and when the song changes, plays or pauses; nothing
 * ticks in between, so a paused song sought from elsewhere meanwhile shows it at the next update.
 */
@Composable
internal fun AmbientScreen(state: AmbientState) {
    val context = LocalContext.current
    // Not with the lifecycle: this is only shown in ambient, where the activity may pause.
    val playing by context.playingSong.collectAsState()
    val songs by context.musicLibrary.songs.collectAsState()
    val song = playing?.let { (songs as? Songs.Loaded)?.byId?.get(it.songId) }
    val scheme = playingSongColors(song?.let(::SongCover)) ?: LocalAppTheme.current.colors
    // As the clock at the top of the screens has it: 21:47, or 9:47.
    val timeFormat = TimeTextDefaults.timeFormat()
    val updates = state.updates
    val time = remember(updates, timeFormat) { DateFormat.format(timeFormat, System.currentTimeMillis()).toString() }
    val shown = remember(updates, playing, song) {
        val player = context.playingSongHolder.now()
        if (song == null || player == null) {
            null
        } else {
            AmbientSong(song.title, song.artist, player.isPlaying, player.positionMs, player.durationMsOf(song))
        }
    }
    AmbientView(
        time,
        shown,
        ambientColors(scheme, state.lowBit),
        shift = if (state.burnInProtection) burnInShift(updates) else DpOffset.Zero,
        lowBit = state.lowBit,
    )
}

/**
 * The always-on display as designed (designs/mockups/ambient.html, E): the [time] big, and under
 * it the [song]'s title (two lines at most), its artist, a bar of how far it is, and the time left
 * in minutes, or where it's paused; with no song, "Gusle". All of it moved by [shift], and on a
 * [lowBit] screen drawn without antialiasing.
 */
@Composable
fun AmbientView(time: String, song: AmbientSong?, colors: AmbientColors, shift: DpOffset, lowBit: Boolean) {
    DesignScale {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .offset(shift.x, shift.y)
                .then(if (lowBit) Modifier.graphicsLayer { renderEffect = LowBitEffect } else Modifier)
                .padding(horizontal = 42.dp, vertical = 34.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                time,
                color = colors.text,
                maxLines = 1,
                style = designText(46, FontWeight.Light, shadowAlpha = 0f, lineHeight = 46.sp)
                    .copy(letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(7.dp))
            if (song == null) {
                Text(
                    stringResource(R.string.app_name),
                    color = colors.secondary,
                    style = designText(12, FontWeight.Normal, shadowAlpha = 0f, lineHeight = 17.5.sp),
                )
            } else {
                SongLines(song, colors)
            }
        }
    }
}

/** The [song]'s title, artist, bar and time left, one under the other. */
@Composable
private fun SongLines(song: AmbientSong, colors: AmbientColors) {
    Text(
        song.title,
        color = colors.text,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = designText(16, FontWeight.SemiBold, shadowAlpha = 0f, lineHeight = 21.sp),
    )
    if (!song.artist.isNullOrBlank()) {
        Text(
            song.artist,
            color = colors.secondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = designText(12, FontWeight.Normal, shadowAlpha = 0f, lineHeight = 17.5.sp),
        )
    }
    val lengthKnown = song.durationMs > 0
    if (lengthKnown) {
        Spacer(Modifier.height(11.dp))
        ProgressBar((song.positionMs.toFloat() / song.durationMs).coerceIn(0f, 1f), colors)
    }
    // A paused song stands still, so its time is shown to the second; a playing one's, to the minute.
    val status = when {
        !song.isPlaying -> stringResource(R.string.paused_at, formatLength(song.positionMs))
        lengthKnown -> minutesLeft(song.positionMs, song.durationMs).let { pluralStringResource(R.plurals.minutes_left, it, it) }
        else -> null
    }
    if (song.isPlaying || status != null) {
        Row(
            Modifier.padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (song.isPlaying) StillEqualizer(colors.accent)
            if (status != null) {
                Text(
                    status,
                    color = colors.secondary,
                    maxLines = 1,
                    style = designText(11, FontWeight.Normal, shadowAlpha = 0f, lineHeight = 14.sp).copy(fontFeatureSettings = "tnum"),
                )
            }
        }
    }
}

/** A bar like the tile's: [fraction] of it played, over its track. */
@Composable
private fun ProgressBar(fraction: Float, colors: AmbientColors) {
    Canvas(Modifier.size(width = 101.dp, height = 2.5.dp)) {
        val ends = CornerRadius(size.height / 2)
        drawRoundRect(colors.track, cornerRadius = ends)
        if (fraction > 0f) drawRoundRect(colors.accent, size = Size(size.width * fraction, size.height), cornerRadius = ends)
    }
}

/**
 * Gusle's sign of a playing song, small and still: three bars in [color], at the heights the lists'
 * equalizer stands still at.
 */
@Composable
private fun StillEqualizer(color: Color) {
    Canvas(Modifier.size(width = 10.5.dp, height = 9.dp)) {
        val barWidth = 2.5.dp.toPx()
        val step = barWidth + 1.5.dp.toPx()
        val ends = CornerRadius(1.dp.toPx())
        StillHeights.forEachIndexed { bar, height ->
            val barHeight = size.height * height
            drawRoundRect(color, Offset(bar * step, size.height - barHeight), Size(barWidth, barHeight), ends)
        }
    }
}

/** A channel from 0 to 255 is turned off below this, and fully on above it. */
private const val LowBitThreshold = 127.5f

/** How steeply: within 5 of the threshold, a channel is off or fully on. */
private const val LowBitSteepness = 51f

/**
 * Every color channel, and the alpha, either off or fully on: a low-bit screen's few colors, and no
 * antialiasing, which the watch's guidelines ask for there. Made only for such a screen.
 */
private val LowBitEffect by lazy {
    AndroidRenderEffect.createColorFilterEffect(
        AndroidColorMatrixFilter(
            FloatArray(20).also { matrix ->
                for (channel in 0 until 4) {
                    matrix[channel * 5 + channel] = LowBitSteepness
                    matrix[channel * 5 + 4] = -LowBitSteepness * LowBitThreshold
                }
            },
        ),
    ).asComposeRenderEffect()
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientPlayingPreview() {
    AmbientPreview(PreviewSong, rememberYamatoColors())
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientPausedPreview() {
    AmbientPreview(PreviewSong.copy(isPlaying = false), rememberYamatoColors())
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientLongTitlePreview() {
    AmbientPreview(PreviewSong.copy(title = "Yamato (Live at Sabaton Open Air, Falun)"), rememberYamatoColors())
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientNoCoverPreview() {
    AmbientPreview(
        AmbientSong("Ghost Division", "Sabaton", isPlaying = true, positionMs = 47_000, durationMs = 231_000),
        AppTheme.Walnut.colors,
    )
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientNothingPreview() {
    AmbientPreview(null, AppTheme.Walnut.colors)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientLowBitPreview() {
    AmbientPreview(PreviewSong, rememberYamatoColors(), lowBit = true)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun AmbientBurnInPreview() {
    AmbientPreview(PreviewSong, rememberYamatoColors(), shift = burnInShift(1))
}

/** Yamato at 1:23 of 3:45, playing. */
private val PreviewSong = AmbientSong("Yamato", "Sabaton", isPlaying = true, positionMs = 83_000, durationMs = 225_000)

/** The colors Gusle makes from the Yamato cover's main color. */
@Composable
private fun rememberYamatoColors(): ColorScheme = remember { seedColorScheme(0xFF93291C.toInt()) }

@Composable
private fun AmbientPreview(
    song: AmbientSong?,
    scheme: ColorScheme,
    shift: DpOffset = DpOffset.Zero,
    lowBit: Boolean = false,
) {
    GusleTheme(colorScheme = scheme) {
        AmbientView("21:47", song, ambientColors(scheme, lowBit), shift, lowBit)
    }
}
