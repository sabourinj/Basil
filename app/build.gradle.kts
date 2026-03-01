plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

base {
    archivesName.set("basil")
}

android {
    namespace = "com.basil.grocyscanner"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.basil.grocyscanner"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.0-AI"

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
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- ANDROID CORE & LIFECYCLE ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // --- JETPACK COMPOSE (UI) ---
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // <-- Your new icons!

    // --- NETWORKING (Retrofit & OkHttp) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- UTILITIES (Images, AI, etc.) ---
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // ... testing libraries below
}