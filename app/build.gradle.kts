plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dresskit.wardrobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dresskit.wardrobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.43.23"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (file("dolabim-keystore.jks").exists()) {
            create("dolabimKey") {
                storeFile = file("dolabim-keystore.jks")
                storePassword = System.getenv("DOLABIM_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("DOLABIM_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("DOLABIM_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("dolabimKey")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("dolabimKey")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    androidResources {
        noCompress += "onnx"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")

    testImplementation("junit:junit:4.13.2")
}
