plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rayo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rayo.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
