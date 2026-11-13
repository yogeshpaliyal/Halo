import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release signing comes from env vars (set by CI from GitHub secrets, or exported locally).
// Without HALO_KEYSTORE_PATH the release build is left unsigned instead of failing.
val releaseKeystore: String? = System.getenv("HALO_KEYSTORE_PATH")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yogeshpaliyal.halo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yogeshpaliyal.halo"
        minSdk = 26
        targetSdk = 36
        // Bumped by the "Release" GitHub workflow; don't edit by hand unless you mean it.
        versionCode = 5
        versionName = "0.1.4"
    }

    signingConfigs {
        // Committed on purpose so every machine and CI build signs debug APKs with the same
        // key, which lets new builds install over old ones. Never use it for Play releases.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("HALO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HALO_KEY_ALIAS")
                keyPassword = System.getenv("HALO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug installs next to the Play/release app: its own package, name and icon.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Lists the app's languages for Android 13+'s per-app language setting
        // (Settings > Apps > Halo > Language). Default language is in res/resources.properties.
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
