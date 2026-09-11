plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.stevebrilien.dshmobile"
    compileSdk = 35

    signingConfigs {
        create("stableDebug") {
            val key = rootProject.file(".mcp/signing/debug.keystore")
            check(key.isFile) {
                "Missing stable debug signing key at ${key.path}. Restore the key; do not silently regenerate it."
            }
            storeFile = key
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.stevebrilien.dshmobile"
        minSdk = 26
        // Deliberately pinned below API 29 for the fixed Android 11 self-hosting target.
        // Android 10+ blocks execve() from writable app home for apps targeting 29+;
        // the local PRoot/Node/DSH runtime requires executable files in app-private storage.
        targetSdk = 28
        versionCode = 12
        versionName = "0.3.0-alpha.11"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Intentional for the fixed Android 11 self-hosting runtime; see ADR 0005.
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:recovery"))
    implementation(project(":core:runtime-api"))
    implementation(project(":core:runtime-android"))
    implementation(project(":core:privilege-api"))
    implementation(project(":core:dsh-api"))
    implementation(project(":core:plugin-api"))

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
