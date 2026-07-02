plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.videofolders.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.videofolders.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // MAIS ESTÁVEL POSSÍVEL (sem conflito no GitHub Actions)
    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")

    implementation("androidx.documentfile:documentfile:1.0.1")
}
