package com.sualtikasifi.cizimhafiza.util

import com.sualtikasifi.cizimhafiza.domain.repository.AuthState

/**
 * Decides whether a just-finished match should interrupt the result screen
 * with a one-time onboarding nudge: a Google sign-in prompt after the very
 * first match ever played on this device, a Play Store rating prompt after
 * the third. Both are evaluated right after [SettingsRepository.lifetimeGamesPlayed]
 * has already been bumped for THIS match (see GameRepositoryImpl.finishSaving,
 * called before either result screen reads it), so the count read here is
 * this match's own, not the one before it.
 *
 * Each prompt is truly one-time: calling either function flips its "shown"
 * flag the moment it decides to show the prompt, not when the player acts
 * on it — so a dismissed or ignored prompt never comes back on a later
 * match either. That is a deliberate choice: this is a first-impressions
 * nudge, not a recurring ask.
 */
object PostMatchPrompts {
    const val SIGN_IN_PROMPT_AT_GAME = 1
    const val RATING_PROMPT_AT_GAME = 3

    /** Paid once, via SettingsRepository.grantRatingBonusXpOnce, when the rating prompt's "Puanla" is tapped. */
    const val RATING_BONUS_XP = 500

    fun shouldShowSignIn(settingsRepository: SettingsRepository, authState: AuthState): Boolean {
        if (settingsRepository.signInPromptShown) return false
        if (settingsRepository.lifetimeGamesPlayed != SIGN_IN_PROMPT_AT_GAME) return false
        if (authState is AuthState.Linked) return false
        settingsRepository.signInPromptShown = true
        return true
    }

    fun shouldShowRating(settingsRepository: SettingsRepository): Boolean {
        if (settingsRepository.ratingPromptShown) return false
        if (settingsRepository.lifetimeGamesPlayed != RATING_PROMPT_AT_GAME) return false
        settingsRepository.ratingPromptShown = true
        return true
    }
}
