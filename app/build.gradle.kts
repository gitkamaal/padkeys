plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.kamaal.padkeys"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kamaal.padkeys"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }
    // Release signing reads from ~/.gradle/gradle.properties (never committed). Without those
    // properties, release builds are unsigned and debug builds work as usual.
    val storeFile = providers.gradleProperty("PADKEYS_STORE_FILE").orNull
    signingConfigs {
        if (storeFile != null) create("release") {
            this.storeFile = file(storeFile)
            storePassword = providers.gradleProperty("PADKEYS_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("PADKEYS_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("PADKEYS_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
