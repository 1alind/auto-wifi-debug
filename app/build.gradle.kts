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
        versionCode = 12
        versionName = "2.1"
    }
    flavorDimensions += "androidVersion"
    productFlavors {
        create("legacy") {
            dimension = "androidVersion"
            applicationIdSuffix = ".legacy"
            minSdk = 26
            targetSdk = 28
            versionCode = 12
            versionName = "2.1-Android8"
        }
        create("modern") {
            dimension = "androidVersion"
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
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
}
