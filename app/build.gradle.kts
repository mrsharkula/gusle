import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

// Release signing comes from keystore.properties (git-ignored, next to this project's root),
// which points to a key kept outside the project. Without a complete file, release builds
// are produced unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = signingKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.sharkula.gusle"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.sharkula.gusle"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // "~/" means the home folder; other relative paths are relative to
                // keystore.properties, i.e. the project root.
                val storePath = keystoreProperties.getProperty("storeFile")
                    .replaceFirst(Regex("^~(?=/|$)"), System.getProperty("user.home"))
                storeFile = rootProject.file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            // Separate package, so debug runs from Android Studio and the release install
            // (signed with a different key) can sit side by side without uninstalling.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Copies of release for the baseline profile plugin (see :baselineprofile), which would
        // otherwise make them itself with release's app ID: nonMinifiedRelease generates the
        // profile, benchmarkRelease measures it. As an app of their own, they install next to the
        // real Gusle and never replace or uninstall it, so they don't need the release key.
        for (name in listOf("nonMinifiedRelease", "benchmarkRelease")) {
            create(name) {
                initWith(getByName("release"))
                applicationIdSuffix = ".benchmark"
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// An unsigned release would install as a different app, so refuse it at the very start of
// the release build instead of quietly producing one. Debug builds don't need the key.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "keystore.properties is missing or incomplete: release builds need " +
                    signingKeys.joinToString()
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)
    implementation(libs.profileinstaller)
    implementation(libs.material.color.utilities)
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
