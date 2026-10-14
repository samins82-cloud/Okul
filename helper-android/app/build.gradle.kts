plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tr.mcoaihl.elakcaller"
    compileSdk = 35

    defaultConfig {
        applicationId = "tr.mcoaihl.elakcaller"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
