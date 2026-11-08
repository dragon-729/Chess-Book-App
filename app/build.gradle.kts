plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes ONLY from environment variables (GitHub Secrets in CI).
// Nothing secret is ever stored in the repository.
val keystorePath: String? = System.getenv("CHESSBOOK_KEYSTORE_PATH")
val hasSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

android {
    namespace = "com.chessbook.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dragon729.chessbook"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("CHESSBOOK_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("CHESSBOOK_VERSION_NAME") ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("CHESSBOOK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHESSBOOK_KEY_ALIAS")
                keyPassword = System.getenv("CHESSBOOK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 shrinking keeps the app small and fast to start.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
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
        jniLibs {
            // Needed so a bundled Stockfish executable (libstockfish.so) is extracted
            // to nativeLibraryDir where Android allows it to be executed.
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Installs the baseline profile (src/main/baseline-prof.txt) for faster, smoother startup.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    testImplementation("junit:junit:4.13.2")
}
