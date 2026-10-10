plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

// Uses Gusle the way a person does, to generate its baseline profile and to measure how fast it
// starts. How to run it: see BaselineProfileGenerator.
android {
    namespace = "com.sharkula.gusle.baselineprofile"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 33
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// On a running device rather than a Gradle managed one: the emulator, which has the test songs.
baselineProfile {
    useConnectedDevices = true
}

// Gradle runs these tests on every device adb sees, and the watch and the phone are often among
// them, so it's refused without one named.
tasks.matching { it.name.startsWith("connected") }.configureEach {
    doFirst {
        val serial = System.getenv("ANDROID_SERIAL")
        if (serial.isNullOrBlank() || "," in serial) {
            throw GradleException("Name the one device to run on, e.g. ANDROID_SERIAL=emulator-5554")
        }
    }
}

dependencies {
    implementation(libs.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// The tests get the app ID of the build they run against.
androidComponents {
    onVariants { variant ->
        val artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { artifactsLoader.load(it)?.applicationId },
        )
    }
}
