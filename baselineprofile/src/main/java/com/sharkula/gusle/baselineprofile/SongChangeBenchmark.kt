package com.sharkula.gusle.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Song changes per iteration, and the time between them: about what a song change takes to settle. */
private const val SongChanges = 5
private const val SongChangeGapMs = 4_000L

/**
 * How smoothly Now playing changes songs: [SongChanges] Nexts in a row, with the screens open that
 * someone listening has (Home → All songs → Now playing). Each iteration starts Gusle afresh, with
 * its data cleared, so every one plays the same songs, from the first, and no cover or color is
 * known yet, as the first time through them. Compiled as installed, with the baseline profile.
 *
 * It also runs on the watch, which has songs of its own, with its serial in ANDROID_SERIAL and
 * without the emulator flag (see [BaselineProfileGenerator]); the watch has to be awake throughout.
 * Its builds are an app of their own, so they leave the installed Gusle alone. Only the watch's
 * numbers count: on the emulator, its GPU is what's slow.
 */
@RunWith(AndroidJUnit4::class)
class SongChangeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** So no music plays on after the last iteration. */
    @After
    fun pause() {
        MacrobenchmarkScope(targetAppId, launchWithClearTask = false)
            .device.executeShellCommand("cmd media_session dispatch pause")
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun nextSongs() = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(
            FrameTimingMetric(),
            MissedFramesMetric(),
            // The longest frame on the main thread, and on the render thread.
            TraceSectionMetric("Choreographer#doFrame %", TraceSectionMetric.Mode.Max, label = "longestUiFrame"),
            TraceSectionMetric("DrawFrames %", TraceSectionMetric.Mode.Max, label = "longestRenderFrame"),
        ),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            // Afresh, rather than with StartupMode.COLD, which would kill it after this block. Also
            // forgets the song it played last, which Home would otherwise work out the colors of.
            device.executeShellCommand("pm clear $packageName")
            allowMusicAccess()
            openHome()
            playFirstSong()
            // The first song's cover and colors are up, and the wave is moving.
            Thread.sleep(SongChangeGapMs)
        },
    ) {
        repeat(SongChanges) {
            device.executeShellCommand("cmd media_session dispatch next")
            Thread.sleep(SongChangeGapMs)
        }
    }
}

/**
 * The frames that missed their deadline, from the system's frame timeline: what `dumpsys gfxinfo`
 * counts as "Frame deadline missed". FrameTimingMetric only gives percentiles, and a few missed
 * frames in a thousand don't reach them.
 */
@OptIn(ExperimentalMetricApi::class)
private class MissedFramesMetric : TraceMetric() {
    override fun getMeasurements(captureInfo: Metric.CaptureInfo, traceSession: TraceProcessor.Session): List<Metric.Measurement> {
        val row = traceSession.query(
            """
            SELECT COUNT(*) AS frames,
                COALESCE(SUM(a.on_time_finish = 0), 0) AS missed,
                COALESCE(MAX(a.dur), 0) AS worst
            FROM actual_frame_timeline_slice a JOIN process p USING (upid)
            WHERE p.name = '${captureInfo.targetPackageName}'
            """.trimIndent(),
        ).single()
        return listOf(
            Metric.Measurement("framesMissed", row.long("missed").toDouble()),
            Metric.Measurement("framesMissedPerSongChange", row.long("missed").toDouble() / SongChanges),
            Metric.Measurement("worstFrameMs", row.long("worst") / 1e6),
        )
    }
}
