import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// AdMob ad unit IDs are never hardcoded — they're read from local.properties
// (gitignored) so real IDs never land in source control. Missing keys fall
// back to Google's public test ad unit IDs so the app still builds/runs.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun adUnitId(key: String, testId: String): String =
    "\"${localProperties.getProperty(key, testId)}\""

android {
    namespace = "com.sualtikasifi.cizimhafiza"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sualtikasifi.cizimhafiza"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google's official public test ad unit IDs — safe defaults until
        // real IDs are supplied via local.properties (ADMOB_*_UNIT_ID).
        buildConfigField("String", "ADMOB_APP_ID", adUnitId("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713"))
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", adUnitId("ADMOB_INTERSTITIAL_UNIT_ID", "ca-app-pub-3940256099942544/1033173712"))
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", adUnitId("ADMOB_REWARDED_UNIT_ID", "ca-app-pub-3940256099942544/5224354917"))

        // The Play Services Ads manifest merger requires this meta-data tag
        // to be present even though we don't call MobileAds.initialize() yet.
        manifestPlaceholders["admobAppId"] =
            localProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    // AdMob SDK — dependency wired up now so the infra (BuildConfig fields,
    // AdManager stub) compiles; no live ad requests are made yet (see
    // ads/AdManager.kt for the deferred call sites).
    implementation(libs.play.services.ads)

    // Firebase (Auth + Firestore) — powers online friend-vs-friend rooms.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
