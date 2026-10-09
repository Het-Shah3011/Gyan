plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gyan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gyan.app"
        minSdk = 31          // Android 12 and above
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"
    }

    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
        }
        create("samsung") {
            dimension = "store"
            applicationIdSuffix = ".samsung"
        }
    }

    signingConfigs {
        create("releaseUpload") {
            val keyPath = providers.gradleProperty("GYAN_RELEASE_STORE_FILE").orNull
            if (keyPath != null) {
                storeFile = file(keyPath)
                storePassword = providers.gradleProperty("GYAN_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("GYAN_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("GYAN_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            if (providers.gradleProperty("GYAN_RELEASE_STORE_FILE").isPresent) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Room (local database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
}
