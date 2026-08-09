import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin.android plugin: AGP 9 has built-in Kotlin support and rejects it.
    alias(libs.plugins.kotlin.compose)
}

/**
 * RevenueCat's Galaxy Store key, read from local.properties (gitignored) or the environment.
 *
 * Empty is a valid state, not a build failure: the app must still compile and run for anyone who
 * clones the repo without a key. Billing degrades to locked rather than crashing.
 */
val revenueCatGalaxyKey: String = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties().apply { local.inputStream().use(::load) }.getProperty("REVENUECAT_GALAXY_KEY")
    } else {
        null
    }
    fromLocal ?: System.getenv("REVENUECAT_GALAXY_KEY") ?: ""
}

android {
    namespace = "com.twofold"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.twofold"
        // Foldable posture APIs and the hinge sensor are the whole product.
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "REVENUECAT_GALAXY_KEY", "\"$revenueCatGalaxyKey\"")

        ndk {
            // arm64 only. Every Galaxy foldable is arm64, and the OCR pipeline ships a ~10MB
            // native library per ABI — carrying x86, x86_64 and armeabi-v7a added 28MB to serve
            // devices this app cannot be used on anyway.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // PdfBox pulls in BouncyCastle whole, including post-quantum test vectors for
            // algorithms no PDF has ever been encrypted with. 5MB of picnic and SIKE parameters.
            excludes += "/org/bouncycastle/pqc/**"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // The foldable posture API. Load-bearing.
    implementation(libs.androidx.window)

    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)

    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.galaxy)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
