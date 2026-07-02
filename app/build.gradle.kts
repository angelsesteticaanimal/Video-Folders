plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.geison.videofolders"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geison.videofolders"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.1-neon-demo"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
}
