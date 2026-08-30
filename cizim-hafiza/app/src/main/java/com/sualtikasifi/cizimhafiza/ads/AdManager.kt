package com.sualtikasifi.cizimhafiza.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.util.GameConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdMob infrastructure — see [GameConstants.ADMOB_ENABLED].
 *
 * Placement plan (per the product brief, to avoid accidental-click policy
 * issues): interstitial after the result screen (never mid-drawing/guessing),
 * rewarded ad offered opt-in for an extra hint / extra time, never auto-shown.
 */
@Singleton
class AdManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val interstitialUnitId = BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID
    private val rewardedUnitId = BuildConfig.ADMOB_REWARDED_UNIT_ID
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // Preloaded ahead of time (see preloadInterstitial) so maybeShowInterstitial
    // can show instantly instead of eating a multi-second network load right
    // at the moment the player reaches the result screen. Plain var, not
    // synchronized: every AdMob SDK callback that touches this is delivered
    // on the main thread, same as every call site here.
    private var cachedInterstitial: InterstitialAd? = null

    // Same single-threaded (main-thread callback) reasoning as
    // cachedInterstitial. rewardedLoading keeps a second preload from being
    // fired while one is already in flight, which would otherwise happen
    // every time a match ends near a hint request.
    private var cachedRewarded: RewardedAd? = null
    private var rewardedLoading = false

    // initializeIfConsented can be reached more than once (a consent form
    // resolving after a configuration change), and MobileAds.initialize is
    // not free to repeat.
    private var initialized = false

    /**
     * Initialises the ads SDK — but only once [ConsentManager] reports that
     * ad requests are permitted for this player's jurisdiction. Calling this
     * unconditionally, as an earlier revision did, requests ads in the EEA
     * with no lawful basis.
     */
    fun initializeIfConsented(consentManager: ConsentManager) {
        if (!consentManager.canRequestAds) {
            Log.d(TAG, "Ads not initialised: consent not granted or unavailable")
            return
        }
        initializeIfEnabled()
    }

    private fun initializeIfEnabled() {
        if (!GameConstants.ADMOB_ENABLED || initialized) return
        initialized = true
        // General/mixed audience (not a children's-only app — see the
        // product decision on target audience): keeps personalized ads
        // available for a healthier eCPM, while MAX_AD_CONTENT_RATING_PG
        // still keeps a family-friendly ceiling on what can show. The Play
        // Console "target audience and content" declaration is a separate,
        // account-side step that has to be done manually — this only
        // configures the SDK's own request behavior.
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build()
        )
        MobileAds.initialize(context) {
            preloadInterstitial()
            preloadRewarded()
        }
    }

    /**
     * Fetches an interstitial in the background and holds onto it until
     * [maybeShowInterstitial] consumes it. A no-op if one is already cached
     * or ads are disabled — safe to call opportunistically (called once at
     * app start, and again every time [maybeShowInterstitial] runs, so the
     * cache is topped back up right after being spent).
     */
    private fun preloadInterstitial() {
        if (!GameConstants.ADMOB_ENABLED || cachedInterstitial != null) return
        InterstitialAd.load(
            context,
            interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cachedInterstitial = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Interstitial preload failed: ${adError.message}")
                }
            }
        )
    }

    /**
     * Call after the result screen is shown, never between drawing/guessing
     * turns. Shows instantly if [preloadInterstitial] already has one ready;
     * only falls back to an on-demand load (with its multi-second delay) on
     * the rare occasion nothing was preloaded yet. A load failure or a
     * disabled flag both fall straight through to [onDismissed] so the
     * result screen is never blocked on an ad. Only actually shows every
     * [INTERSTITIAL_EVERY_N_MATCHES]th call — single-player and online
     * matches share one counter, persisted in SharedPreferences so the
     * cadence survives an app restart.
     */
    fun maybeShowInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (!GameConstants.ADMOB_ENABLED) {
            onDismissed()
            return
        }
        val matchCount = prefs.getInt(KEY_MATCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_MATCH_COUNT, matchCount).apply()
        if (matchCount % INTERSTITIAL_EVERY_N_MATCHES != 0) {
            preloadInterstitial() // keep the cache warm for next time either way
            onDismissed()
            return
        }

        val preloaded = cachedInterstitial
        if (preloaded != null) {
            cachedInterstitial = null
            preloaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    onDismissed()
                    preloadInterstitial()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    onDismissed()
                    preloadInterstitial()
                }
            }
            preloaded.show(activity)
            return
        }

        // Nothing preloaded (e.g. the very first match right after a cold
        // start, before the initial preload finished) — fall back to an
        // on-demand load exactly as before.
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

    /**
     * Fetches a rewarded ad in the background and holds it until
     * [maybeShowRewarded] consumes it. Same rationale as
     * [preloadInterstitial], but the payoff is larger: a rewarded ad is
     * always requested at a moment the game clock is deliberately stopped
     * (see GameViewModel.useHint), so every second spent loading is a second
     * the player sits looking at a frozen countdown and a "Yükleniyor…"
     * label wondering whether the button worked.
     */
    private fun preloadRewarded() {
        if (!GameConstants.ADMOB_ENABLED || cachedRewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            context,
            rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    cachedRewarded = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedLoading = false
                    Log.d(TAG, "Rewarded preload failed: ${adError.message}")
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

        // onReward drives resuming a paused game countdown, so it must fire
        // exactly once no matter which SDK callback path is taken — a
        // double-resume would start two competing timer coroutines, and a
        // missed one would leave the round frozen forever.
        var settled = false
        val settle: (Boolean) -> Unit = { earned ->
            if (!settled) {
                settled = true
                onReward(earned)
                preloadRewarded() // top the cache back up for next time
            }
        }

        fun show(ad: RewardedAd) {
            // onAdDismissedFullScreenContent always fires once the ad closes,
            // whether the viewer watched to completion or skipped early — by
            // then `earned` already reflects whether the reward callback
            // fired first, so skipping early correctly resolves as no reward.
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = settle(earned)
                override fun onAdFailedToShowFullScreenContent(adError: AdError) = settle(false)
            }
            ad.show(activity) { earned = true }
        }

        val preloaded = cachedRewarded
        if (preloaded != null) {
            cachedRewarded = null
            show(preloaded)
            return
        }

        RewardedAd.load(
            context,
            rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = show(ad)

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Rewarded ad failed to load: ${adError.message}")
                    settle(false)
                }
            }
        )
    }

    private companion object {
        const val TAG = "AdManager"
        const val PREFS_NAME = "ad_manager_prefs"
        const val KEY_MATCH_COUNT = "interstitial_match_count"
        const val INTERSTITIAL_EVERY_N_MATCHES = 3
    }
}
