package com.sualtikasifi.cizimhafiza.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sualtikasifi.cizimhafiza.BuildConfig

/**
 * Opens the app's Play Store listing directly.
 *
 * Used to go through Play Core's in-app review sheet first. That API is
 * quota-limited per user and simply does nothing — no dialog, no error — on
 * a device without the Play Store or on a sideloaded build, and it never
 * reports which case it hit, so there was nothing to fall back from: the
 * Settings row's tap just read as a dead button. Going straight to the
 * store listing is deterministic on every install.
 */
object AppReviewLauncher {

    fun openStoreListing(activity: Activity) {
        val marketUri = Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}")
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
        } catch (e: ActivityNotFoundException) {
            // No Play Store app (an emulator, or a device without Play
            // Services) — the web listing still works in a browser.
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            } catch (e2: ActivityNotFoundException) {
                Log.w("AppReviewLauncher", "No activity can open the Play listing", e2)
            }
        }
    }
}
