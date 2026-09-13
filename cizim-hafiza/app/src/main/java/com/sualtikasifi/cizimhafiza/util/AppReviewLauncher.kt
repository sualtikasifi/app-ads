package com.sualtikasifi.cizimhafiza.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.sualtikasifi.cizimhafiza.BuildConfig

/**
 * Opens Play's in-app review sheet, falling back to the store listing.
 *
 * The sheet is entirely Play's to decide on: it is quota-limited per user
 * and simply does nothing on a device without the Play Store, on a
 * sideloaded build, or when the quota is spent — and it never reports which
 * of those happened, by design. That is fine for a prompt the app raises on
 * its own, but this one is behind a row in Settings that the player
 * deliberately tapped, and a tap that visibly does nothing reads as broken.
 * So a failed or ignored request falls through to the store page, where
 * they can always leave a rating.
 */
object AppReviewLauncher {

    fun launch(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { request ->
                if (!request.isSuccessful) {
                    openStoreListing(activity)
                    return@addOnCompleteListener
                }
                manager.launchReviewFlow(activity, request.result)
                    .addOnFailureListener { openStoreListing(activity) }
            }
    }

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
