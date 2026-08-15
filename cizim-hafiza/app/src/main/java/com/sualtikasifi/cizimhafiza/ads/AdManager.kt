package com.sualtikasifi.cizimhafiza.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.util.GameConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdMob infrastructure only — see [GameConstants.ADMOB_ENABLED]. No live ad
 * requests are made yet: MobileAds.initialize() and the actual
 * InterstitialAd/RewardedAd loaders are intentionally left as TODOs so
 * turning ads on later is a one-flag change plus filling these in.
 *
 * Placement plan (per the product brief, to avoid accidental-click policy
 * issues): interstitial after the result screen (never mid-drawing/guessing),
 * rewarded ad offered opt-in for an extra hint / extra time, never auto-shown.
 */
@Singleton
class AdManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val interstitialUnitId = BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID
    private val rewardedUnitId = BuildConfig.ADMOB_REWARDED_UNIT_ID

    fun initializeIfEnabled() {
        if (!GameConstants.ADMOB_ENABLED) return
        // TODO: MobileAds.initialize(context)
        Log.d(TAG, "AdMob init skipped — ADMOB_ENABLED is false")
    }

    /** Call after the result screen is shown, never between drawing/guessing turns. */
    fun maybeShowInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (!GameConstants.ADMOB_ENABLED) {
            onDismissed()
            return
        }
        // TODO: load + show InterstitialAd using interstitialUnitId, call onDismissed() from its FullScreenContentCallback
        onDismissed()
    }

    /** User-initiated only (e.g. "extra hint" button) — never auto-triggered. */
    fun maybeShowRewarded(activity: Activity, onReward: (Boolean) -> Unit) {
        if (!GameConstants.ADMOB_ENABLED) {
            onReward(false)
            return
        }
        // TODO: load + show RewardedAd using rewardedUnitId, call onReward(true) from onUserEarnedReward
        onReward(false)
    }

    private companion object {
        const val TAG = "AdManager"
    }
}
