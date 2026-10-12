package com.sharkula.gusle.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Gusle's baseline profile: the code it runs on its common paths, which Android compiles
 * ahead of time at install, so launching, scrolling and the player are smooth from the first use.
 * Generate it again after changing those paths, and commit what it writes to
 * `app/src/release/generated/baselineProfiles/`. The hand-written rules in
 * `app/src/main/baseline-prof.txt` are added to it at build time.
 *
 * It runs on the Wear OS emulator (API 33+, no root needed), which has the test songs. Its builds
 * are an app of their own, com.sharkula.gusle.benchmark, which Gradle installs next to Gusle and
 * uninstalls when done. Gradle would run on every connected device, so it needs the emulator named
 * in ANDROID_SERIAL. From the project folder (from a terminal, `./gradlew` needs
 * `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`):
 * ```
 * scripts/push-test-songs.sh --many emulator-5554   # 53 songs, so the lists scroll
 * adb -s emulator-5554 shell svc power stayon true  # the always-on display gets in the way
 * ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 * ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.sharkula.gusle.baselineprofile.StartupBenchmarks \
 *     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
 * adb -s emulator-5554 shell svc power stayon false
 * ```
 * Generating takes about 20 minutes: the rule repeats each journey until the profile stays the
 * same. The second Gradle command is [StartupBenchmarks], to see the difference the profile makes;
 * Macrobenchmark refuses emulators unless told otherwise, as their timings differ from a watch's.
 * Its results are under `baselineprofile/build/outputs/connected_android_test_additional_output/`.
 * Reduce motion must be off on the emulator, or the animations' code isn't profiled.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun rememberASong() = MacrobenchmarkScope(targetAppId, launchWithClearTask = true).rememberFirstSong()

    /**
     * Opening Gusle to Home. Also its startup profile, which R8 uses to put this code together
     * in the DEX file.
     */
    @Test
    fun startup() = rule.collect(targetAppId, includeInStartupProfile = true, strictStability = true) {
        allowMusicAccess()
        openHome()
    }

    /**
     * Every screen, as a person uses them. The rule repeats it until the profile stays the same.
     */
    @Test
    fun everyScreen() = rule.collect(targetAppId, strictStability = true) {
        allowMusicAccess()
        openHome()
        useEveryScreen()
    }
}
