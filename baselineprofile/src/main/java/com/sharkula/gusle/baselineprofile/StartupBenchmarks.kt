package com.sharkula.gusle.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long Gusle takes to open cold, to its first frame and to Home with the songs and the song it
 * comes back to: compiled as installed without its baseline profile, and with it. The difference
 * is what the profile gains. How to run it: see [BaselineProfileGenerator].
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun rememberASong() = MacrobenchmarkScope(targetAppId, launchWithClearTask = true).rememberFirstSong()

    @Test
    fun withoutProfile() = startup(CompilationMode.None())

    @Test
    fun withProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @OptIn(ExperimentalMetricApi::class)
    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(
            StartupTimingMetric(),
            // The first frame is drawn while the songs load, and it's empty: Home comes a few
            // frames later. These are the main thread's frames until Home shows the songs, added up.
            TraceSectionMetric("Choreographer#doFrame %", label = "framesUntilHome"),
        ),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { allowMusicAccess() },
    ) {
        openHome()
    }
}
