package com.sualtikasifi.cizimhafiza.util

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty

/**
 * Single place to tune game balance / feature toggles without hunting
 * through screens and ViewModels.
 */
object GameConstants {

    // --- Word count choices offered on the selection screen ---
    val WORD_COUNT_OPTIONS = listOf(10, 20, 30, 40, 50)

    // --- Drawing phase duration per difficulty. RELAXED (see GameMode) skips
    // the timer entirely, so this only applies to the NORMAL mode. ---
    fun drawingDurationSeconds(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 7
        Difficulty.HARD -> 10
    }

    // Last N seconds of the drawing countdown trigger the warning color + vibration.
    const val WARNING_THRESHOLD_SECONDS = 2

    // Break shown between the drawing phase and the guessing phase.
    const val BREAK_DURATION_SECONDS = 3

    // Time limit to answer each guess. Timing out counts as skipped (wrong/0 points).
    const val GUESS_DURATION_SECONDS = 10

    // --- Scoring ---
    const val POINTS_CORRECT = 5
    const val POINTS_WRONG = 0

    // Feature flag: toggle the speed bonus system on/off in one place.
    const val SPEED_BONUS_ENABLED = true
    const val SPEED_BONUS_THRESHOLD_MS = 3_000L
    const val SPEED_BONUS_POINTS = 2

    // "Yakın doğru" toleransı: normalize edilmiş cevaplar arasındaki
    // Levenshtein mesafesi bu değere eşit veya altındaysa doğru sayılır.
    const val ANSWER_LEVENSHTEIN_TOLERANCE = 2

    // Feature flag: AdMob is wired up (BuildConfig, AdManager) but not yet
    // making live ad requests. Flip this once real ad unit IDs are ready.
    const val ADMOB_ENABLED = false
}
