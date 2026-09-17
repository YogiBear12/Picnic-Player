import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin Android support is built into AGP 9+ — no kotlin.android plugin needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.androidx.baselineprofile)
}

// Version name is derived from the latest git tag (e.g. `v0.1.0` -> `0.1.0`).
// The build is triggered manually, but always stamps the most recent tag.
// Falls back to a default when there are no tags / no git (e.g. source archive).
val versionNameFromTag: String =
    runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
            isIgnoreExitValue = true
        }.standardOutput
            .asText
            .get()
            .trim()
            .removePrefix("v")
    }.getOrNull()?.ifBlank { null } ?: "0.1.0"

// Human-readable build date shown on the About page (e.g. "14 Jul 2026").
val buildDate: String = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())

// versionCode derived from the same tag so updates install as proper upgrades
// (equal versionCode is a reinstall; downgrade protection needs it increasing).
val versionCodeFromTag: Int = versionNameFromTag
    .split(".")
    .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    .takeIf { it.size == 3 }
    ?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
    ?: 1

// In-app updater release host (#111): a GitHub/Gitea releases API base URL, e.g.
// "https://api.github.com/repos/<owner>/<repo>". Resolved from the UPDATE_REPO
// environment variable (CI injects a repo secret), falling back to an
// `updateRepo=` entry in local.properties (device testing; gitignored). Blank
// disables the updater entirely, so ad-hoc builds never phone anywhere.
val updateRepo: String = run {
    val fromEnv: String? = System.getenv("UPDATE_REPO")
    val fromLocal: String? = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { file ->
            val props = Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("updateRepo")
        }
    fromEnv?.takeIf { it.isNotBlank() } ?: fromLocal?.takeIf { it.isNotBlank() } ?: ""
}

android {
    namespace = "app.picnic.player"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.picnic.player"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeFromTag
        versionName = versionNameFromTag
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        // Launcher label (product name for release; the debug build overrides it so
        // both APKs are distinguishable side by side). About screen uses R.string.app_name.
        manifestPlaceholders["appLabel"] = "Picnic Player"
        // One ABI folder is installed per app, so a narrower set than libass ships would
        // leave arm64 devices with no decoder.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Release signing pulls the keystore + credentials from environment/secrets
    // (see .github/workflows/build-android-tv.yml). A release build ALWAYS uses
    // the release key — if the secrets are absent or incomplete it fails loudly,
    // never silently falling back to the debug key (which would mask a broken
    // release key). Debug builds are unaffected: the throw only fires when a
    // release task is actually requested.
    val releaseKeystoreBase64: String? = System.getenv("ANDROID_KEYSTORE_BASE64")
    // Only a genuine shipping-release task must fail without the release key. The
    // androidx.baselineprofile plugin builds throwaway release-type variants
    // (nonMinifiedRelease / benchmarkRelease) to capture the profile on a local device —
    // those are non-shipping and are debug-signed below, so they must not trip the guard.
    val releaseBuildRequested = gradle.startParameter.taskNames.any { name ->
        name.contains("Release") &&
            !name.contains("NonMinified", ignoreCase = true) &&
            !name.contains("Benchmark", ignoreCase = true) &&
            !name.contains("BaselineProfile", ignoreCase = true)
    }
    signingConfigs {
        create("release") {
            when {
                !releaseKeystoreBase64.isNullOrBlank() -> {
                    val storePw = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                    val alias = System.getenv("ANDROID_KEY_ALIAS")
                    val keyPw = System.getenv("ANDROID_KEY_PASSWORD")
                    if (releaseBuildRequested &&
                        (storePw.isNullOrBlank() || alias.isNullOrBlank() || keyPw.isNullOrBlank())
                    ) {
                        throw GradleException(
                            "Release signing is incomplete: ANDROID_KEYSTORE_BASE64 is set but one of " +
                                "ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD is missing."
                        )
                    }
                    val keystoreFile = layout.buildDirectory
                        .file("release.keystore")
                        .get()
                        .asFile
                    keystoreFile.parentFile.mkdirs()
                    keystoreFile.writeBytes(Base64.getDecoder().decode(releaseKeystoreBase64.trim()))
                    storeFile = keystoreFile
                    storePassword = storePw
                    keyAlias = alias
                    keyPassword = keyPw
                }
                releaseBuildRequested -> throw GradleException(
                    "Release build requested but ANDROID_KEYSTORE_BASE64 is not set. Release APKs must be " +
                        "signed with the release key; refusing to fall back to the debug key."
                )
                else -> {
                    // No release secrets and no shipping-release task: the only release-type
                    // build that can run here is the baseline-profile plugin's local variant.
                    // Sign it with the standard debug key so it installs on the test device.
                    val debugStore = file("${System.getProperty("user.home")}/.android/debug.keystore")
                    if (debugStore.exists()) {
                        storeFile = debugStore
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                }
            }
        }
    }

    buildTypes {
        debug {
            // Unique package so a debug build installs alongside a release build for
            // testing (issue #108). Version + launcher label are suffixed to match.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Picnic Player (Debug)"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Always the release key — never debug. Missing secrets fail above.
            signingConfig = signingConfigs.getByName("release")
        }
        // The androidx.baselineprofile plugin creates the nonMinifiedRelease /
        // benchmarkRelease variants it needs to capture the profile — no hand-rolled
        // benchmark build type required.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Let android.util.Log (and other stubbed framework calls) return defaults
            // instead of throwing in JVM unit tests — WebSocketManager logs socket state.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

val ffmpegDecoderAar = file("libs/media3-decoder-ffmpeg-${libs.versions.media3.get()}.aar")

// A missing AAR otherwise surfaces as an unresolved-symbol wall in the Kotlin compiler.
val checkFfmpegDecoderAar = tasks.register("checkFfmpegDecoderAar") {
    doLast {
        if (!ffmpegDecoderAar.exists()) {
            throw GradleException(
                "Missing ${ffmpegDecoderAar.name}. Build it with:\n" +
                    "  tools/build-ffmpeg-decoder.sh \$ANDROID_NDK_HOME"
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkFfmpegDecoderAar)
}

aboutLibraries {
    // Collapse per-variant/per-platform artifact rows (e.g. jellyfin-core +
    // jellyfin-core-android-debug) into one entry each. The .android plugin
    // generates R.raw.aboutlibraries automatically at build time.
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
    // A local AAR carries no POM, so the plugin cannot see FFmpeg. config/ declares it by hand.
    collect {
        configPath = file("config")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.datasource.okhttp)
    // Versioned path: a media3 bump must fail here, not load a decoder built against another core.
    implementation(files(ffmpegDecoderAar))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Official Jellyfin Kotlin SDK (LGPL-3.0) — typed API, auth, Quick Connect,
    // discovery, image URLs, device profiles.
    implementation(libs.jellyfin.core)

    // libass (peerless2012) — advanced ASS/SSA subtitle rendering for media3.
    implementation(libs.ass.media)

    implementation(libs.exoplayer.hdr.utils)

    // ZXing core (Apache-2.0) — QR generation for Quick Connect pairing.
    implementation(libs.zxing.core)

    // AboutLibraries core (Apache-2.0) — parses the dependency/license metadata
    // (R.raw.aboutlibraries, generated by the .android plugin at build time). The
    // licenses UI is our own (see ui/settings), so the compose modules aren't used.
    implementation(libs.aboutlibraries.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.profileinstaller)

    // Merges the generated baseline profile (from the :baselineprofile module) into the
    // release APK; profileinstaller above installs it at first run.
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

ktlint {
    // Pin the ktlint engine so local checks match the CI gate exactly (the plugin's
    // bundled default lagged behind CI, letting newer standard: rules slip through).
    version.set("1.5.0")
    android.set(true)
    ignoreFailures.set(false)
}
