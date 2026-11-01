plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.mariechantal.heuresolaire"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.mariechantal.heuresolaire"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
}
