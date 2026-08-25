package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * Snapshot of the durable, lifetime counters an achievement condition can
 * check — deliberately NOT sourced from the `game_sessions` table, which is
 * pruned to the most recent N rows (see GameSessionDao.pruneOlderThan) and
 * so cannot answer "how many words has this player EVER drawn". Everything
 * here except [hadPerfectRoundThisSave] comes from SettingsRepository's
 * never-shrinking SharedPreferences-backed counters.
 */
data class AchievementStats(
    val lifetimeWordsDrawn: Int,
    val lifetimeScore: Int,
    val currentStreak: Int,
    /** True only when the game just saved was a perfect round (every word guessed correctly). */
    val hadPerfectRoundThisSave: Boolean
)

/**
 * A fixed, local, deterministic catalog — no server round-trip, no config
 * to fetch. [name] (the enum constant's own name, e.g. "FIRST_GAME") is
 * persisted as the unlocked-achievement id (see UnlockedAchievementEntity),
 * so renaming a constant here would orphan its saved unlock — add new
 * achievements as new constants instead of renaming existing ones.
 */
enum class Achievement(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val emoji: String,
    val isUnlocked: (AchievementStats) -> Boolean
) {
    // Always true by the time this is evaluated — the check only ever runs
    // right after a game finishes saving (see GameRepositoryImpl), so
    // "played at least one game" is already guaranteed.
    FIRST_GAME(
        R.string.achievement_first_game_title, R.string.achievement_first_game_desc, "🎉",
        { true }
    ),
    WORDS_10(
        R.string.achievement_words_10_title, R.string.achievement_words_10_desc, "✏️",
        { it.lifetimeWordsDrawn >= 10 }
    ),
    WORDS_100(
        R.string.achievement_words_100_title, R.string.achievement_words_100_desc, "🖊️",
        { it.lifetimeWordsDrawn >= 100 }
    ),
    WORDS_500(
        R.string.achievement_words_500_title, R.string.achievement_words_500_desc, "🖌️",
        { it.lifetimeWordsDrawn >= 500 }
    ),
    PERFECT_ROUND(
        R.string.achievement_perfect_round_title, R.string.achievement_perfect_round_desc, "💯",
        { it.hadPerfectRoundThisSave }
    ),
    STREAK_3(
        R.string.achievement_streak_3_title, R.string.achievement_streak_3_desc, "🔥",
        { it.currentStreak >= 3 }
    ),
    STREAK_7(
        R.string.achievement_streak_7_title, R.string.achievement_streak_7_desc, "🔥",
        { it.currentStreak >= 7 }
    ),
    SCORE_250(
        R.string.achievement_score_250_title, R.string.achievement_score_250_desc, "⭐",
        { it.lifetimeScore >= 250 }
    ),
    SCORE_1000(
        R.string.achievement_score_1000_title, R.string.achievement_score_1000_desc, "🏆",
        { it.lifetimeScore >= 1000 }
    )
}
