package com.sualtikasifi.cizimhafiza.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.util.GameConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers advertising consent through Google's User Messaging Platform
 * before any ad is requested.
 *
 * This is not optional polish. AdMob's own policy requires a certified CMP
 * for traffic from the EEA, the UK and Switzerland, and serving personalised
 * ads there without a lawful basis is a GDPR breach — so an app that calls
 * `MobileAds.initialize` and starts requesting ads, as this one used to, is
 * non-compliant from the first European install. UMP also covers the
 * separate US state privacy signals AdMob applies through the same form.
 *
 * The flow is deliberately quiet for players outside a region that requires
 * a form: [ensureConsent] resolves immediately and no dialog is ever shown.
 *
 * **One-time console setup** (nothing here can substitute for it): in the
 * AdMob console open Privacy & messaging → GDPR (and → US state regulations),
 * create and publish a message for this app. Until a message is published,
 * `requestConsentInfoUpdate` reports that no form is available and the app
 * proceeds without one.
 */
@Singleton
class ConsentManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    /**
     * True once UMP says ad requests are permitted. Checked before
     * initialising the ads SDK — see [AdManager.initializeIfEnabled].
     */
    val canRequestAds: Boolean
        get() = !GameConstants.ADMOB_ENABLED || consentInformation.canRequestAds()

    /**
     * Requests the current consent status and, if a form is required in this
     * player's jurisdiction, shows it. [onResolved] runs exactly once
     * afterwards — on success, on failure, and when no form is needed — so
     * the caller can go ahead and initialise ads either way. A consent
     * *failure* is not a reason to block the game: it only means no ad can
     * be requested, which [canRequestAds] already reflects.
     */
    fun ensureConsent(activity: Activity, onResolved: () -> Unit) {
        if (!GameConstants.ADMOB_ENABLED) {
            onResolved()
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                // In debug builds every device is treated as if it were in
                // the EEA, so the form can actually be exercised during
                // development instead of only ever appearing for European
                // users after release. Never enabled in a release build.
                if (BuildConfig.DEBUG) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(context)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build()
                    )
                }
            }
            .build()

        var settled = false
        val settle = {
            if (!settled) {
                settled = true
                onResolved()
            }
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.d(TAG, "Consent form: ${formError.message}")
                    }
                    settle()
                }
            },
            { requestError ->
                // No network, or no published message yet. Ads simply stay
                // unavailable (canRequestAds reports false); the game itself
                // is unaffected.
                Log.d(TAG, "Consent info update failed: ${requestError.message}")
                settle()
            }
        )
    }

    /**
     * True when the player's jurisdiction lets them reopen the consent form
     * on demand — drives whether Settings shows a "Reklam tercihleri" row at
     * all, since offering it to someone who has no form to open would be
     * confusing.
     */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Reopens the consent form from Settings, so a choice can be changed later — itself a GDPR requirement. */
    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.d(TAG, "Privacy options form: ${error.message}")
            onDismissed()
        }
    }

    private companion object {
        const val TAG = "ConsentManager"
    }
}
