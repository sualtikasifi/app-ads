package com.sualtikasifi.cizimhafiza.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sualtikasifi.cizimhafiza.BuildConfig

/**
 * Opens the app's Play Store listing directly, in the Play Store app itself.
 *
 * Used to go through Play Core's in-app review sheet first. That API is
 * quota-limited per user and simply does nothing — no dialog, no error — on
 * a device without the Play Store or on a sideloaded build, and it never
 * reports which case it hit, so there was nothing to fall back from: the
 * Settings row's tap just read as a dead button. Going straight to the
 * store listing is deterministic on every install.
 */
object AppReviewLauncher {

    /** Google Play's own package name — never varies across devices. */
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun openStoreListing(activity: Activity) {
        val marketUri = Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")
        // setPackage targets the market:// intent at Play Store specifically.
        // Without it, a device that also has another store registered for
        // that scheme (MIUI's GetApps, Samsung's Galaxy Store, ...) shows a
        // "which app?" chooser instead of just opening Play — technically
        // not broken, but not what a "rate the app" tap should ever surface.
        val playStoreIntent = Intent(Intent.ACTION_VIEW, marketUri).setPackage(PLAY_STORE_PACKAGE)
        try {
            activity.startActivity(playStoreIntent)
        } catch (e: ActivityNotFoundException) {
            // Play Store isn't installed (an emulator, a device without Play
            // Services, or it really is a different vendor's store) — the
            // web listing still works in whatever browser is default.
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            } catch (e2: ActivityNotFoundException) {
                Log.w("AppReviewLauncher", "No activity can open the Play listing", e2)
            }
        }
    }
}
