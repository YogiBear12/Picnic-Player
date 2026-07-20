plugins {
    // Kotlin Android support is built into AGP 9+ — no kotlin.android plugin needed.
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "app.picnic.player.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Macrobenchmark/baseline-profile capture needs API 28+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The app whose startup the profile is captured against.
    targetProjectPath = ":app"
}

// Capture on a physically connected device (the Google TV, API 34) rather than a
// Gradle Managed Device — non-rooted on-device profiling works on API 33+.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
