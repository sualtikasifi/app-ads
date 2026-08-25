package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * Snapshot of the durable, lifetime counters an achievement condition can
 * check — deliberately NOT sourced from the `game_sessions` table, which is
 * pruned to the most recent N rows (see GameSessionDao.pruneOlderThan) and
 * so cannot answer "how many words has this player EVER drawn". Everything
 * here comes from SettingsRepository's never-shrinking SharedPreferences
 * counters, so achievements stay earned no matter how old the games get.
 */
data class AchievementStats(
    val gamesPlayed: Int,
    val lifetimeWordsDrawn: Int,
    val lifetimeScore: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val perfectRounds: Int,
    val onlineWins: Int
)

/**
 * A fixed, local, deterministic catalog — no server round-trip, no config
 * to fetch. [name] (the enum constant's own name, e.g. "FIRST_GAME") is
 * persisted as the unlocked-achievement id (see UnlockedAchievementEntity),
 * so renaming a constant here would orphan its saved unlock — add new
 * achievements as new constants instead of renaming existing ones.
 *
 * Tiers are deliberately spread from "first session" all the way out to
 * goals that take months of regular play, so there's always a next one in
 * sight rather than everything unlocking in the first week.
 */
enum class Achievement(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val emoji: String,
    val isUnlocked: (AchievementStats) -> Boolean
) {
    // --- Games played ---
    FIRST_GAME(
        R.string.achievement_first_game_title, R.string.achievement_first_game_desc, "🎉",
        { it.gamesPlayed >= 1 }
    ),
    GAMES_10(
        R.string.achievement_games_10_title, R.string.achievement_games_10_desc, "🎮",
        { it.gamesPlayed >= 10 }
    ),
    GAMES_50(
        R.string.achievement_games_50_title, R.string.achievement_games_50_desc, "🕹️",
        { it.gamesPlayed >= 50 }
    ),
    GAMES_200(
        R.string.achievement_games_200_title, R.string.achievement_games_200_desc, "👑",
        { it.gamesPlayed >= 200 }
    ),

    // --- Words drawn ---
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
    WORDS_1000(
        R.string.achievement_words_1000_title, R.string.achievement_words_1000_desc, "🎨",
        { it.lifetimeWordsDrawn >= 1000 }
    ),
    WORDS_2500(
        R.string.achievement_words_2500_title, R.string.achievement_words_2500_desc, "🏛️",
        { it.lifetimeWordsDrawn >= 2500 }
    ),

    // --- Perfect rounds ---
    PERFECT_ROUND(
        R.string.achievement_perfect_round_title, R.string.achievement_perfect_round_desc, "💯",
        { it.perfectRounds >= 1 }
    ),
    PERFECT_10(
        R.string.achievement_perfect_10_title, R.string.achievement_perfect_10_desc, "🎯",
        { it.perfectRounds >= 10 }
    ),
    PERFECT_50(
        R.string.achievement_perfect_50_title, R.string.achievement_perfect_50_desc, "🧠",
        { it.perfectRounds >= 50 }
    ),

    // --- Daily streaks ---
    STREAK_3(
        R.string.achievement_streak_3_title, R.string.achievement_streak_3_desc, "🔥",
        { it.currentStreak >= 3 || it.bestStreak >= 3 }
    ),
    STREAK_7(
        R.string.achievement_streak_7_title, R.string.achievement_streak_7_desc, "🔥",
        { it.currentStreak >= 7 || it.bestStreak >= 7 }
    ),
    STREAK_30(
        R.string.achievement_streak_30_title, R.string.achievement_streak_30_desc, "📅",
        { it.currentStreak >= 30 || it.bestStreak >= 30 }
    ),
    STREAK_100(
        R.string.achievement_streak_100_title, R.string.achievement_streak_100_desc, "🗓️",
        { it.currentStreak >= 100 || it.bestStreak >= 100 }
    ),

    // --- Lifetime score ---
    SCORE_250(
        R.string.achievement_score_250_title, R.string.achievement_score_250_desc, "⭐",
        { it.lifetimeScore >= 250 }
    ),
    SCORE_1000(
        R.string.achievement_score_1000_title, R.string.achievement_score_1000_desc, "🏆",
        { it.lifetimeScore >= 1000 }
    ),
    SCORE_5000(
        R.string.achievement_score_5000_title, R.string.achievement_score_5000_desc, "💎",
        { it.lifetimeScore >= 5000 }
    ),
    SCORE_15000(
        R.string.achievement_score_15000_title, R.string.achievement_score_15000_desc, "🌟",
        { it.lifetimeScore >= 15000 }
    ),

    // --- Online wins ---
    ONLINE_WIN_1(
        R.string.achievement_online_win_1_title, R.string.achievement_online_win_1_desc, "🥇",
        { it.onlineWins >= 1 }
    ),
    ONLINE_WIN_10(
        R.string.achievement_online_win_10_title, R.string.achievement_online_win_10_desc, "🏅",
        { it.onlineWins >= 10 }
    ),
    ONLINE_WIN_50(
        R.string.achievement_online_win_50_title, R.string.achievement_online_win_50_desc, "🛡️",
        { it.onlineWins >= 50 }
    )
}
