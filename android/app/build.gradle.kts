plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.aboutlibraries)
}
android {
    namespace = "com.elejar.ZentraDL"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()
    ndkVersion = libs.versions.ndk.get()
    defaultConfig {
        applicationId = "com.elejar.ZentraDL"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "armeabi-v7a", "x86_64"); isUniversalApk = true } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    packaging { jniLibs { useLegacyPackaging = false } } // 16 KB page alignment via NDK r28+
}
dependencies {
    implementation(project(":android:engine"))
    implementation(project(":android:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore.prefs)
    implementation(libs.coil.compose); implementation(libs.coil.network)
    implementation(libs.media3.exoplayer); implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash); implementation(libs.media3.session)
    implementation(libs.workmanager)
    implementation(libs.webkit)
    implementation(libs.glance.appwidget)
    implementation(libs.serialization.json)
    implementation(libs.timber)
    implementation(libs.aboutlibraries.m3)
    testImplementation(libs.junit4); testImplementation(libs.truth)
    testImplementation(libs.turbine); testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
