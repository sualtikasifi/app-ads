plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sualtikasifi.cizimhafiza.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28 // BaselineProfileRule.collect requires API 28+
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // This module has no release logic of its own — it only ever runs the
    // profile generator against :app's "release" variant (matched below).
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

baselineProfile {
    // Uses the managed device declared above instead of requiring a
    // physically connected device/emulator, so `./gradlew
    // :app:generateBaselineProfile` can run unattended (e.g. in CI) once a
    // machine with KVM/hardware acceleration executes it — see
    // BASELINE_PROFILE.md for the one-time setup this still needs.
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}
