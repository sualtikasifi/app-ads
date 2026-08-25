package com.sualtikasifi.cizimhafiza.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
        MobileAds.initialize(context)
    }

    /**
     * Call after the result screen is shown, never between drawing/guessing
     * turns. Loads on demand (no preloading yet — a future improvement) and
     * shows as soon as it's ready; a load failure or a disabled flag both
     * fall straight through to [onDismissed] so the result screen is never
     * blocked on an ad.
     */
    fun maybeShowInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (!GameConstants.ADMOB_ENABLED) {
            onDismissed()
            return
        }
        InterstitialAd.load(
            context,
            interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() = onDismissed()
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) = onDismissed()
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Interstitial failed to load: ${adError.message}")
                    onDismissed()
                }
            }
        )
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
