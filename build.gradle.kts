// Root build — plugins are declared here (apply false) and applied per module.
plugins {
    // Kotlin Android support is built into AGP 9+ — the kotlin.android plugin is gone.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.aboutlibraries) apply false
}
