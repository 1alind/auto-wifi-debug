plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alind.autowifidebug"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.alind.autowifidebug"
        minSdk = 30
        targetSdk = 35
        versionCode = 10
        versionName = "1.9"
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
}
