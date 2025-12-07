plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.neirotech"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.neirotech"
        minSdk = 27
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

    packaging {
        jniLibs {
            pickFirsts.add("lib/x86_64/libc++_shared.so")
            pickFirsts.add("lib/x86/libc++_shared.so")
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // BrainBit SDK
    implementation("com.github.BrainbitLLC:neurosdk2:1.0.6.34")
    implementation("com.github.BrainbitLLC:Emotional-state-artifacts:1.0.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.airbnb.android:lottie-compose:6.1.0")
    // Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Compose
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.48")

    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")


    // Bluetooth
    implementation("no.nordicsemi.android:ble:2.7.5")
    implementation("no.nordicsemi.android:ble-ktx:2.7.5")

    // Video Player
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")

    // Charts/Graphs
    implementation("com.patrykandpatrick.vico:compose:1.13.0")

    // PDF Generation
    implementation("com.itextpdf:itext7-core:7.2.5")

    // GIF/Images
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

}