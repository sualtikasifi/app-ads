package com.sualtikasifi.cizimhafiza.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the class/method usage a cold app launch actually exercises, so
 * ART can pre-compile that path into the shipped APK's baseline profile
 * (see :app's `implementation(libs.androidx.profileinstaller)`) instead of
 * interpreting/JIT-warming it on every user's first launch — startup is
 * where a baseline profile pays off the most (see the module's own
 * BASELINE_PROFILE.md for the one-time device/CI setup this needs and why
 * it can't be generated inside this repo's own sandboxed environment).
 *
 * Deliberately just cold-start, not a full user journey (opening a game,
 * drawing, guessing): every extra UiAutomator selector here is one more
 * thing that can silently stop matching after a screen is redesigned,
 * quietly producing an incomplete profile instead of a failing one.
 * Startup is also, by a wide margin, where a baseline profile matters
 * most — see https://d.android.com/topic/performance/baselineprofiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.sualtikasifi.cizimhafiza"
    ) {
        pressHome()
        startActivityAndWait()

        // Waits for *some* content to render before ending the trace — the
        // main menu isn't identified by a specific selector on purpose (see
        // the class doc): a `hasObject` on the package itself only asserts
        // "the app actually drew something", which is exactly the boundary
        // a startup-only profile needs and nothing more.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
    }
}
