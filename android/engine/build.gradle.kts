plugins {
    alias(libs.plugins.android.lib)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.elejar.ZentraDL.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    tasks.withType<org.gradle.api.tasks.testing.Test> {
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
dependencies {
    implementation(libs.okhttp)
    implementation(libs.bt.core)
    implementation(libs.bt.http.tracker)
    implementation(libs.bt.dht)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.timber)
    testImplementation(libs.junit4); testImplementation(libs.truth); testImplementation(libs.turbine)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.mockwebserver)
}
