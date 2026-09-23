plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.elak.okulum"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elak.okulum"
        minSdk = 24
        targetSdk = 35
        versionCode = 33
        versionName = "0.9.0"
        manifestPlaceholders["appLabel"] = "ELAK Okulum"
    }

    buildTypes {
        getByName("debug") {
            // GitHub hosted runners create a different temporary debug signing key
            // on each build. Use a separate package id for test APKs so they can be
            // installed alongside the production app without signature conflicts.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            manifestPlaceholders["appLabel"] = "ELAK Okulum Test"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
