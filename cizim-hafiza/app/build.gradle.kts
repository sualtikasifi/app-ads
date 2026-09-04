import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
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

// Release signing, same local.properties-gated pattern as the AdMob IDs
// above: the keystore path/passwords are never committed. See
// RELEASE_SIGNING.md for how to generate the keystore and fill these in.
// Until they're set, the release build type simply has no signing config
// (as before) — local `assembleRelease`/`compileDebugKotlin` builds keep
// working; only an actual Play Store upload needs this filled in.
val releaseKeystorePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.sualtikasifi.cizimhafiza"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sualtikasifi.cizimhafiza"
        minSdk = 26
        targetSdk = 36
        // Bump BOTH on every build handed to a device. A rebuild that keeps
        // the previous versionCode is not an upgrade as far as Android is
        // concerned: the installer may leave the old app in place, and
        // nothing on screen distinguishes the two builds. See the version
        // line on the Settings screen, which prints these back.
        versionCode = 22
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google's official public test ad unit IDs — safe defaults until
        // real IDs are supplied via local.properties (ADMOB_*_UNIT_ID).
        buildConfigField("String", "ADMOB_APP_ID", adUnitId("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713"))
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", adUnitId("ADMOB_INTERSTITIAL_UNIT_ID", "ca-app-pub-3940256099942544/1033173712"))
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", adUnitId("ADMOB_REWARDED_UNIT_ID", "ca-app-pub-3940256099942544/5224354917"))

        // The Play Services Ads manifest merger requires this meta-data tag
        // to be present regardless of build variant — MobileAds.initialize()
        // runs in every build type (see GameConstants.ADMOB_ENABLED /
        // ads/AdManager.kt).
        manifestPlaceholders["admobAppId"] =
            localProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// See :baselineprofile/BaselineProfileGenerator.kt and BASELINE_PROFILE.md —
// generateReleaseBaselineProfile compiles that module's instrumented test
// against a Gradle Managed Device and copies its output into
// src/main/baselineProfiles/baseline-prof.txt for profileinstaller to ship.
baselineProfile {
    // AGP 9.0.1 (this project's version) is newer than what this plugin
    // version was tested against — a benign version-skew warning, not an
    // incompatibility (verified: every generateBaselineProfile/merge/copy
    // task wires up correctly under it).
    warnings {
        maxAgpVersion = false
    }
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    // Only for per-app language switching (AppCompatDelegate.setApplicationLocales) —
    // MainActivity extends AppCompatActivity just to make that API work, everything
    // else about the UI stays 100% Compose.
    implementation(libs.androidx.appcompat)

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

    // WorkManager — schedules the once-a-day "come back and play" reminder
    // (notifications/DailyEngagementWorker.kt), + Hilt's worker-injection glue.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    // AdMob SDK — dependency wired up now so the infra (BuildConfig fields,
    // AdManager stub) compiles; no live ad requests are made yet (see
    // ads/AdManager.kt for the deferred call sites).
    implementation(libs.play.services.ads)
    implementation(libs.play.review)

    // Google's User Messaging Platform — gathers the GDPR/IAB TCF consent
    // that serving ads in the EEA and UK legally requires, and that AdMob's
    // own policy requires regardless of where the app is listed. Without it
    // a published app is in breach the moment a European device opens it
    // (see ads/ConsentManager.kt).
    implementation(libs.user.messaging.platform)

    // Firebase (Auth + Firestore) — powers online friend-vs-friend rooms.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)
    // Crashlytics: 60+ awaited Firestore calls and a large Compose surface
    // mean field crashes are otherwise completely invisible — there is no
    // other channel telling us what breaks on real devices.
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics.ktx)
    // Task<T>.await() used throughout OnlineGameRepositoryImpl.
    implementation(libs.kotlinx.coroutines.play.services)

    // Applies the packaged baseline profile on first run — a Compose-heavy
    // app pays a large first-launch JIT cost without it.
    implementation(libs.androidx.profileinstaller)

    // Cloud backup / account linking (see AuthRepositoryImpl.kt): Credential
    // Manager is the current, non-deprecated way to ask for a Google ID
    // token, which upgrades this device's anonymous Firebase session to a
    // permanent one without losing its uid (and therefore its friends,
    // rooms and history).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
