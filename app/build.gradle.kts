import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: set these env vars (CI reads them from repo secrets).
// Without them, release builds fall back to the debug key so CI always produces an APK.
val keystorePath: String? = System.getenv("BITMAPS_KEYSTORE")

android {
    namespace = "com.bitdroid.maps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bitdroid.maps"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0"

        // Pixels are arm64 only; keeping one ABI keeps the APK (map engine + voice engine) small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    androidResources {
        noCompress += listOf("onnx")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("BITMAPS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BITMAPS_KEY_ALIAS")
                keyPassword = System.getenv("BITMAPS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

base {
    archivesName.set("bitMaps-${android.defaultConfig.versionName}")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Map rendering (open source, no API key) and HTTP for search + routing.
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Natural offline voices (Piper models) and unpacking downloaded voices.
    implementation(files("libs/sherpa-onnx.aar")) // downloaded by CI, see .github/workflows/build.yml
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
}
