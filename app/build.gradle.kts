plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dcpi.audiostickers"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dcpi.audiostickers"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.7.0-beta1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
}
