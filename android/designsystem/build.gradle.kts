plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.elejar.ZentraDL.designsystem"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
