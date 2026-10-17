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
        versionCode = 6
        versionName = "1.2.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
