// Root build — shared config lives in module build files + libs.versions.toml.
// NOTE: AGP 9+ bundles Kotlin (no kotlin-android plugin anywhere).
plugins {
    alias(libs.plugins.android.app) apply false
    alias(libs.plugins.android.lib) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
