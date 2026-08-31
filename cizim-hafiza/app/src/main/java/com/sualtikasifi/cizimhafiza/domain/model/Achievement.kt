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
 * Declaration order is the difficulty order, easiest first — this is what
 * both the Achievements screen's grid and this file itself read top to
 * bottom as, so a new tier must be inserted at its actual difficulty
 * position among the other 50, not appended at the end. There are six
 * independent ladders (games played, words drawn, perfect rounds, daily
 * streaks, lifetime score, online wins), and their tiers are interleaved
 * here by an estimated "typical games needed to reach it" — a streak day
 * or an online win is weighted heavier than a raw game/word/point count,
 * since both take real calendar time or a harder-to-win match rather than
 * just more solo play. The exact weighting is a judgment call, not a
 * formula the game enforces; nudge an entry's position if actual player
 * data ever shows it landing very differently from its neighbours.
 */
enum class Achievement(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val emoji: String,
    /** Awarded once, the moment this unlocks — see GameRepositoryImpl.finishSaving. */
    val xpReward: Int,
    val isUnlocked: (AchievementStats) -> Boolean
) {
    WORDS_10(
        R.string.achievement_words_10_title, R.string.achievement_words_10_desc, "✏️", 50,
        { it.lifetimeWordsDrawn >= 10 }
    ),
    SCORE_50(
        R.string.achievement_score_50_title, R.string.achievement_score_50_desc, "🌱", 30,
        { it.lifetimeScore >= 50 }
    ),
    FIRST_GAME(
        R.string.achievement_first_game_title, R.string.achievement_first_game_desc, "🎉", 50,
        { it.gamesPlayed >= 1 }
    ),
    STREAK_1(
        R.string.achievement_streak_1_title, R.string.achievement_streak_1_desc, "☀️", 50,
        { it.currentStreak >= 1 || it.bestStreak >= 1 }
    ),
    WORDS_50(
        R.string.achievement_words_50_title, R.string.achievement_words_50_desc, "📝", 75,
        { it.lifetimeWordsDrawn >= 50 }
    ),
    GAMES_3(
        R.string.achievement_games_3_title, R.string.achievement_games_3_desc, "🎲", 65,
        { it.gamesPlayed >= 3 }
    ),
    SCORE_250(
        R.string.achievement_score_250_title, R.string.achievement_score_250_desc, "⭐", 75,
        { it.lifetimeScore >= 250 }
    ),
    STREAK_3(
        R.string.achievement_streak_3_title, R.string.achievement_streak_3_desc, "🔥", 100,
        { it.currentStreak >= 3 || it.bestStreak >= 3 }
    ),
    WORDS_100(
        R.string.achievement_words_100_title, R.string.achievement_words_100_desc, "🖊️", 100,
        { it.lifetimeWordsDrawn >= 100 }
    ),
    ONLINE_WIN_1(
        R.string.achievement_online_win_1_title, R.string.achievement_online_win_1_desc, "🥇", 100,
        { it.onlineWins >= 1 }
    ),
    PERFECT_ROUND(
        R.string.achievement_perfect_round_title, R.string.achievement_perfect_round_desc, "💯", 100,
        { it.perfectRounds >= 1 }
    ),
    GAMES_10(
        R.string.achievement_games_10_title, R.string.achievement_games_10_desc, "🎮", 100,
        { it.gamesPlayed >= 10 }
    ),
    STREAK_7(
        R.string.achievement_streak_7_title, R.string.achievement_streak_7_desc, "🔥", 250,
        { it.currentStreak >= 7 || it.bestStreak >= 7 }
    ),
    SCORE_1000(
        R.string.achievement_score_1000_title, R.string.achievement_score_1000_desc, "🏆", 200,
        { it.lifetimeScore >= 1000 }
    ),
    ONLINE_WIN_3(
        R.string.achievement_online_win_3_title, R.string.achievement_online_win_3_desc, "🎖️", 180,
        { it.onlineWins >= 3 }
    ),
    STREAK_14(
        R.string.achievement_streak_14_title, R.string.achievement_streak_14_desc, "🌤️", 450,
        { it.currentStreak >= 14 || it.bestStreak >= 14 }
    ),
    WORDS_500(
        R.string.achievement_words_500_title, R.string.achievement_words_500_desc, "🖌️", 250,
        { it.lifetimeWordsDrawn >= 500 }
    ),
    GAMES_25(
        R.string.achievement_games_25_title, R.string.achievement_games_25_desc, "🃏", 150,
        { it.gamesPlayed >= 25 }
    ),
    PERFECT_5(
        R.string.achievement_perfect_5_title, R.string.achievement_perfect_5_desc, "🔎", 180,
        { it.perfectRounds >= 5 }
    ),
    SCORE_2500(
        R.string.achievement_score_2500_title, R.string.achievement_score_2500_desc, "💰", 350,
        { it.lifetimeScore >= 2500 }
    ),
    STREAK_30(
        R.string.achievement_streak_30_title, R.string.achievement_streak_30_desc, "📅", 800,
        { it.currentStreak >= 30 || it.bestStreak >= 30 }
    ),
    WORDS_1000(
        R.string.achievement_words_1000_title, R.string.achievement_words_1000_desc, "🎨", 500,
        { it.lifetimeWordsDrawn >= 1000 }
    ),
    GAMES_50(
        R.string.achievement_games_50_title, R.string.achievement_games_50_desc, "🕹️", 250,
        { it.gamesPlayed >= 50 }
    ),
    ONLINE_WIN_10(
        R.string.achievement_online_win_10_title, R.string.achievement_online_win_10_desc, "🏅", 300,
        { it.onlineWins >= 10 }
    ),
    PERFECT_10(
        R.string.achievement_perfect_10_title, R.string.achievement_perfect_10_desc, "🎯", 300,
        { it.perfectRounds >= 10 }
    ),
    SCORE_5000(
        R.string.achievement_score_5000_title, R.string.achievement_score_5000_desc, "💎", 600,
        { it.lifetimeScore >= 5000 }
    ),
    STREAK_60(
        R.string.achievement_streak_60_title, R.string.achievement_streak_60_desc, "🌙", 1300,
        { it.currentStreak >= 60 || it.bestStreak >= 60 }
    ),
    GAMES_100(
        R.string.achievement_games_100_title, R.string.achievement_games_100_desc, "🏹", 400,
        { it.gamesPlayed >= 100 }
    ),
    ONLINE_WIN_25(
        R.string.achievement_online_win_25_title, R.string.achievement_online_win_25_desc, "🎗️", 550,
        { it.onlineWins >= 25 }
    ),
    WORDS_2500(
        R.string.achievement_words_2500_title, R.string.achievement_words_2500_desc, "🏛️", 1000,
        { it.lifetimeWordsDrawn >= 2500 }
    ),
    STREAK_100(
        R.string.achievement_streak_100_title, R.string.achievement_streak_100_desc, "🗓️", 2000,
        { it.currentStreak >= 100 || it.bestStreak >= 100 }
    ),
    GAMES_200(
        R.string.achievement_games_200_title, R.string.achievement_games_200_desc, "👑", 600,
        { it.gamesPlayed >= 200 }
    ),
    SCORE_15000(
        R.string.achievement_score_15000_title, R.string.achievement_score_15000_desc, "🌟", 1500,
        { it.lifetimeScore >= 15000 }
    ),
    ONLINE_WIN_50(
        R.string.achievement_online_win_50_title, R.string.achievement_online_win_50_desc, "🛡️", 900,
        { it.onlineWins >= 50 }
    ),
    WORDS_5000(
        R.string.achievement_words_5000_title, R.string.achievement_words_5000_desc, "🖼️", 1800,
        { it.lifetimeWordsDrawn >= 5000 }
    ),
    STREAK_200(
        R.string.achievement_streak_200_title, R.string.achievement_streak_200_desc, "🌗", 3500,
        { it.currentStreak >= 200 || it.bestStreak >= 200 }
    ),
    PERFECT_50(
        R.string.achievement_perfect_50_title, R.string.achievement_perfect_50_desc, "🧠", 800,
        { it.perfectRounds >= 50 }
    ),
    SCORE_30000(
        R.string.achievement_score_30000_title, R.string.achievement_score_30000_desc, "💵", 3000,
        { it.lifetimeScore >= 30000 }
    ),
    GAMES_500(
        R.string.achievement_games_500_title, R.string.achievement_games_500_desc, "⚔️", 1200,
        { it.gamesPlayed >= 500 }
    ),
    WORDS_10000(
        R.string.achievement_words_10000_title, R.string.achievement_words_10000_desc, "🏺", 3000,
        { it.lifetimeWordsDrawn >= 10000 }
    ),
    ONLINE_WIN_100(
        R.string.achievement_online_win_100_title, R.string.achievement_online_win_100_desc, "⚡", 1800,
        { it.onlineWins >= 100 }
    ),
    STREAK_365(
        R.string.achievement_streak_365_title, R.string.achievement_streak_365_desc, "🎆", 6000,
        { it.currentStreak >= 365 || it.bestStreak >= 365 }
    ),
    PERFECT_100(
        R.string.achievement_perfect_100_title, R.string.achievement_perfect_100_desc, "🧩", 1500,
        { it.perfectRounds >= 100 }
    ),
    SCORE_50000(
        R.string.achievement_score_50000_title, R.string.achievement_score_50000_desc, "🏦", 5000,
        { it.lifetimeScore >= 50000 }
    ),
    GAMES_1000(
        R.string.achievement_games_1000_title, R.string.achievement_games_1000_desc, "🗡️", 2000,
        { it.gamesPlayed >= 1000 }
    ),
    ONLINE_WIN_250(
        R.string.achievement_online_win_250_title, R.string.achievement_online_win_250_desc, "👊", 3500,
        { it.onlineWins >= 250 }
    ),
    WORDS_25000(
        R.string.achievement_words_25000_title, R.string.achievement_words_25000_desc, "🏰", 5000,
        { it.lifetimeWordsDrawn >= 25000 }
    ),
    SCORE_100000(
        R.string.achievement_score_100000_title, R.string.achievement_score_100000_desc, "💠", 9000,
        { it.lifetimeScore >= 100000 }
    ),
    PERFECT_250(
        R.string.achievement_perfect_250_title, R.string.achievement_perfect_250_desc, "🌌", 3000,
        { it.perfectRounds >= 250 }
    ),
    GAMES_2000(
        R.string.achievement_games_2000_title, R.string.achievement_games_2000_desc, "🏔️", 3500,
        { it.gamesPlayed >= 2000 }
    ),
    WORDS_50000(
        R.string.achievement_words_50000_title, R.string.achievement_words_50000_desc, "🗿", 8000,
        { it.lifetimeWordsDrawn >= 50000 }
    )
}
