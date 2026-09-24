import java.util.Base64

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val testKeystore = rootProject.file("test-signing/elak-test.jks")
val testKeystoreB64 = rootProject.file("test-signing/elak-test.jks.b64")
if (!testKeystore.exists() && testKeystoreB64.exists()) {
    testKeystore.parentFile.mkdirs()
    testKeystore.writeBytes(Base64.getMimeDecoder().decode(testKeystoreB64.readText().trim()))
}

android {
    namespace = "com.elak.okulum"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elak.okulum"
        minSdk = 24
        targetSdk = 35
        versionCode = 41
        versionName = "0.9.5"
        manifestPlaceholders["appLabel"] = "ELAK Okulum"
    }

    signingConfigs {
        create("test") {
            storeFile = testKeystore
            storePassword = "elaktest2026"
            keyAlias = "elaktest"
            keyPassword = "elaktest2026"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            manifestPlaceholders["appLabel"] = "ELAK Okulum Test"
            signingConfig = signingConfigs.getByName("test")
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
