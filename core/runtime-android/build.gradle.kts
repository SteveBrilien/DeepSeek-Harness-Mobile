plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.stevebrilien.dshmobile.core.runtimeandroid"
    compileSdk = 35

    defaultConfig { minSdk = 26 }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    implementation(project(":core:model"))
    implementation(project(":core:dsh-api"))
    implementation(project(":core:recovery"))
    implementation(project(":core:runtime-api"))
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.15.1")
    // Robolectric 4.15.1 resolves Conscrypt 2.5.2, whose uber JAR has no Linux ARM64 JNI.
    // 2.6.2 adds linux-aarch_64 and remains compatible with this ARM64 test host.
    testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.6.2")
}
