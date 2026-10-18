import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has built-in Kotlin support, so there is no kotlin.android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.androidx.baselineprofile)
}

// A source archive has no git, hence the fallback.
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

val buildDate: String = SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())

// Must increase with every tag, or an update installs as a reinstall or is refused as a downgrade.
val versionCodeFromTag: Int = versionNameFromTag
    .split(".")
    .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
    .takeIf { it.size == 3 }
    ?.let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }
    ?: 1

// Releases API base URL for the in-app updater. Blank disables the updater, so ad-hoc builds never contact a server.
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
        manifestPlaceholders["appLabel"] = "Picnic Player"
        // One ABI folder is installed per app, so a narrower set than libass ships would
        // leave arm64 devices with no decoder.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // A shipping release never falls back to the debug key: that would hide a broken release key.
    val releaseKeystoreBase64: String? = System.getenv("ANDROID_KEYSTORE_BASE64")
    // The baseline-profile plugin's release-type variants never ship, so they are exempt.
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
            signingConfig = signingConfigs.getByName("release")
        }
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
            // Stubbed framework calls such as android.util.Log would otherwise throw in JVM tests.
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
val libassVersion = libs.versions.libassAndroid.get()
val libassPoms = listOf("ass-kt", "ass-media").map { file("libs/m2/io/github/peerless2012/$it/$libassVersion/$it-$libassVersion.pom") }

// A missing local build output otherwise surfaces as an unresolved-symbol wall in the Kotlin compiler.
val checkLocalAars = tasks.register("checkLocalAars") {
    val buildHints = mapOf(ffmpegDecoderAar to "tools/build-ffmpeg-decoder.sh \$ANDROID_NDK_HOME") +
        libassPoms.associateWith { "tools/build-libass.sh /path/to/libass-android" }
    doLast {
        val missing = buildHints.filterKeys { !it.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                missing.entries.joinToString("\n") { (aar, hint) -> "Missing ${aar.name}. Build it with: $hint" }
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(checkLocalAars)
}

aboutLibraries {
    // One row per library instead of one per variant artifact (jellyfin-core, jellyfin-core-android-debug).
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
    // Neither the FFmpeg AAR nor the natives inside libass.so carry a POM, so config/ declares them by hand.
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

    implementation(libs.jellyfin.core)

    implementation(libs.ass.kt)
    implementation(libs.ass.media)

    implementation(libs.exoplayer.hdr.utils)

    implementation(libs.zxing.core)

    // The licenses screen is our own, so only the core is needed.
    implementation(libs.aboutlibraries.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

ktlint {
    // The plugin's bundled engine lags, letting newer standard rules slip past the gate.
    version.set("1.5.0")
    android.set(true)
    ignoreFailures.set(false)
}
