plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.theone"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.theone"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    // Glide for video thumbnail
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // ExoPlayer for video playback
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}