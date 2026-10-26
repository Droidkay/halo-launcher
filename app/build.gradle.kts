plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.droidkay.halo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.droidkay.halo"
        minSdk = 31
        targetSdk = 34
        // Every GitHub build gets a higher number so new APKs install over the old one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    signingConfigs {
        create("release") {
            storeFile = file("halo-release.jks")
            storePassword = "halo-launcher"
            keyAlias = "halo"
            keyPassword = "halo-launcher"
        }
    }

    flavorDimensions += "device"
    productFlavors {
        create("fold") {
            dimension = "device"
            applicationId = "com.droidkay.halo"
            resValue("string", "app_name", "Halo")
            buildConfigField("boolean", "IS_PAD", "false")
            buildConfigField("boolean", "IS_DUO", "false")
        }
        // Halo Duo: separate iOS-style launcher for the Fold that leaves gestures to the system.
        create("duo") {
            dimension = "device"
            applicationId = "com.droidkay.haloduo"
            resValue("string", "app_name", "Halo Duo")
            buildConfigField("boolean", "IS_PAD", "false")
            buildConfigField("boolean", "IS_DUO", "true")
        }
        create("pad") {
            dimension = "device"
            applicationId = "com.droidkay.halopad"
            resValue("string", "app_name", "Halo Pad")
            buildConfigField("boolean", "IS_PAD", "true")
            buildConfigField("boolean", "IS_DUO", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
