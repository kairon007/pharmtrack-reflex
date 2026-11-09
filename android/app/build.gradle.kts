// Phase 4: the live Android app. A thin Compose layer over the pure :core module —
// the app module owns the Android services (file store, notifications, SharedPreferences)
// and nothing else; every decision still lives in :core.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pharmtrack.reflex.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tunnxo.medtrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 28
        versionName = "1.0.0"
    }

    // The upload key from the shipping app, carried over from the Skip build. The file is
    // gitignored; without it a release build cannot be signed and Play rejects the upload.
    signingConfigs {
        val keystorePropertiesFile = file("keystore.properties")
        create("release") {
            if (keystorePropertiesFile.isFile) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            } else {
                println("release signing: app/keystore.properties is missing — a release build will NOT be signed")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // The rewrite ships unshrunk on purpose: the first 1.0 build should not change
            // behaviour that the parity suite has verified.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))

    // :core declares these as `implementation`, so the app states them again: the app
    // compiles against kotlinx-serialization's annotations (Instant carries a custom
    // serializer) and against the coroutines API it calls (flushStore, StateFlow).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
}
