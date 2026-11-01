plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mariechantal.cadransolaire"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.mariechantal.cadransolaire"
        // Watch Face Format v2 : Wear OS 4 (API 33) minimum.
        minSdk = 33
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
}
