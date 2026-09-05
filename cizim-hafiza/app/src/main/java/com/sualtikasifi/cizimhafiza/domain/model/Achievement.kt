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
    val onlineWins: Int,
    val lifetimeXp: Int
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
 * position among the other 100, not appended at the end. There are seven
 * independent ladders (games played, words drawn, perfect rounds, daily
 * streaks, lifetime score, online wins, lifetime XP), and their tiers are
 * interleaved
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
    FIRST_GAME(
        R.string.achievement_first_game_title, R.string.achievement_first_game_desc, "🎉", 50,
        { it.gamesPlayed >= 1 }
    ),
    SCORE_50(
        R.string.achievement_score_50_title, R.string.achievement_score_50_desc, "🌱", 50,
        { it.lifetimeScore >= 50 }
    ),
    WORDS_10(
        R.string.achievement_words_10_title, R.string.achievement_words_10_desc, "✏️", 50,
        { it.lifetimeWordsDrawn >= 10 }
    ),
    XP_250(
        R.string.achievement_xp_250_title, R.string.achievement_xp_250_desc, "🔋", 50,
        { it.lifetimeXp >= 250 }
    ),
    SCORE_100(
        R.string.achievement_score_100_title, R.string.achievement_score_100_desc, "🍀", 75,
        { it.lifetimeScore >= 100 }
    ),
    GAMES_3(
        R.string.achievement_games_3_title, R.string.achievement_games_3_desc, "🎲", 75,
        { it.gamesPlayed >= 3 }
    ),
    PERFECT_ROUND(
        R.string.achievement_perfect_round_title, R.string.achievement_perfect_round_desc, "💯", 75,
        { it.perfectRounds >= 1 }
    ),
    WORDS_25(
        R.string.achievement_words_25_title, R.string.achievement_words_25_desc, "🖇️", 75,
        { it.lifetimeWordsDrawn >= 25 }
    ),
    XP_500(
        R.string.achievement_xp_500_title, R.string.achievement_xp_500_desc, "⚙️", 75,
        { it.lifetimeXp >= 500 }
    ),
    GAMES_5(
        R.string.achievement_games_5_title, R.string.achievement_games_5_desc, "🎳", 75,
        { it.gamesPlayed >= 5 }
    ),
    ONLINE_WIN_1(
        R.string.achievement_online_win_1_title, R.string.achievement_online_win_1_desc, "🥇", 75,
        { it.onlineWins >= 1 }
    ),
    SCORE_250(
        R.string.achievement_score_250_title, R.string.achievement_score_250_desc, "⭐", 75,
        { it.lifetimeScore >= 250 }
    ),
    STREAK_1(
        R.string.achievement_streak_1_title, R.string.achievement_streak_1_desc, "☀️", 75,
        { it.currentStreak >= 1 || it.bestStreak >= 1 }
    ),
    WORDS_50(
        R.string.achievement_words_50_title, R.string.achievement_words_50_desc, "📝", 75,
        { it.lifetimeWordsDrawn >= 50 }
    ),
    XP_1000(
        R.string.achievement_xp_1000_title, R.string.achievement_xp_1000_desc, "🧪", 75,
        { it.lifetimeXp >= 1000 }
    ),
    GAMES_10(
        R.string.achievement_games_10_title, R.string.achievement_games_10_desc, "🎮", 100,
        { it.gamesPlayed >= 10 }
    ),
    PERFECT_3(
        R.string.achievement_perfect_3_title, R.string.achievement_perfect_3_desc, "🪄", 100,
        { it.perfectRounds >= 3 }
    ),
    SCORE_500(
        R.string.achievement_score_500_title, R.string.achievement_score_500_desc, "✨", 100,
        { it.lifetimeScore >= 500 }
    ),
    GAMES_15(
        R.string.achievement_games_15_title, R.string.achievement_games_15_desc, "🎯", 100,
        { it.gamesPlayed >= 15 }
    ),
    STREAK_2(
        R.string.achievement_streak_2_title, R.string.achievement_streak_2_desc, "🌥️", 100,
        { it.currentStreak >= 2 || it.bestStreak >= 2 }
    ),
    WORDS_100(
        R.string.achievement_words_100_title, R.string.achievement_words_100_desc, "🖊️", 100,
        { it.lifetimeWordsDrawn >= 100 }
    ),
    ONLINE_WIN_3(
        R.string.achievement_online_win_3_title, R.string.achievement_online_win_3_desc, "🎖️", 100,
        { it.onlineWins >= 3 }
    ),
    PERFECT_5(
        R.string.achievement_perfect_5_title, R.string.achievement_perfect_5_desc, "🔎", 100,
        { it.perfectRounds >= 5 }
    ),
    XP_2500(
        R.string.achievement_xp_2500_title, R.string.achievement_xp_2500_desc, "🔮", 125,
        { it.lifetimeXp >= 2500 }
    ),
    STREAK_3(
        R.string.achievement_streak_3_title, R.string.achievement_streak_3_desc, "🔥", 125,
        { it.currentStreak >= 3 || it.bestStreak >= 3 }
    ),
    GAMES_25(
        R.string.achievement_games_25_title, R.string.achievement_games_25_desc, "🃏", 125,
        { it.gamesPlayed >= 25 }
    ),
    SCORE_1000(
        R.string.achievement_score_1000_title, R.string.achievement_score_1000_desc, "🏆", 125,
        { it.lifetimeScore >= 1000 }
    ),
    ONLINE_WIN_5(
        R.string.achievement_online_win_5_title, R.string.achievement_online_win_5_desc, "🏵️", 125,
        { it.onlineWins >= 5 }
    ),
    PERFECT_10(
        R.string.achievement_perfect_10_title, R.string.achievement_perfect_10_desc, "🎯", 150,
        { it.perfectRounds >= 10 }
    ),
    STREAK_5(
        R.string.achievement_streak_5_title, R.string.achievement_streak_5_desc, "🌈", 150,
        { it.currentStreak >= 5 || it.bestStreak >= 5 }
    ),
    WORDS_250(
        R.string.achievement_words_250_title, R.string.achievement_words_250_desc, "📐", 150,
        { it.lifetimeWordsDrawn >= 250 }
    ),
    XP_5000(
        R.string.achievement_xp_5000_title, R.string.achievement_xp_5000_desc, "🌀", 150,
        { it.lifetimeXp >= 5000 }
    ),
    GAMES_50(
        R.string.achievement_games_50_title, R.string.achievement_games_50_desc, "🕹️", 175,
        { it.gamesPlayed >= 50 }
    ),
    STREAK_7(
        R.string.achievement_streak_7_title, R.string.achievement_streak_7_desc, "🔥", 175,
        { it.currentStreak >= 7 || it.bestStreak >= 7 }
    ),
    ONLINE_WIN_10(
        R.string.achievement_online_win_10_title, R.string.achievement_online_win_10_desc, "🏅", 175,
        { it.onlineWins >= 10 }
    ),
    SCORE_2500(
        R.string.achievement_score_2500_title, R.string.achievement_score_2500_desc, "💰", 200,
        { it.lifetimeScore >= 2500 }
    ),
    GAMES_75(
        R.string.achievement_games_75_title, R.string.achievement_games_75_desc, "🎪", 200,
        { it.gamesPlayed >= 75 }
    ),
    STREAK_10(
        R.string.achievement_streak_10_title, R.string.achievement_streak_10_desc, "🌞", 200,
        { it.currentStreak >= 10 || it.bestStreak >= 10 }
    ),
    WORDS_500(
        R.string.achievement_words_500_title, R.string.achievement_words_500_desc, "🖌️", 200,
        { it.lifetimeWordsDrawn >= 500 }
    ),
    XP_10000(
        R.string.achievement_xp_10000_title, R.string.achievement_xp_10000_desc, "☄️", 200,
        { it.lifetimeXp >= 10000 }
    ),
    ONLINE_WIN_15(
        R.string.achievement_online_win_15_title, R.string.achievement_online_win_15_desc, "🎽", 225,
        { it.onlineWins >= 15 }
    ),
    GAMES_100(
        R.string.achievement_games_100_title, R.string.achievement_games_100_desc, "🏹", 225,
        { it.gamesPlayed >= 100 }
    ),
    PERFECT_25(
        R.string.achievement_perfect_25_title, R.string.achievement_perfect_25_desc, "🦉", 225,
        { it.perfectRounds >= 25 }
    ),
    STREAK_14(
        R.string.achievement_streak_14_title, R.string.achievement_streak_14_desc, "🌤️", 250,
        { it.currentStreak >= 14 || it.bestStreak >= 14 }
    ),
    WORDS_750(
        R.string.achievement_words_750_title, R.string.achievement_words_750_desc, "🧵", 250,
        { it.lifetimeWordsDrawn >= 750 }
    ),
    SCORE_5000(
        R.string.achievement_score_5000_title, R.string.achievement_score_5000_desc, "💎", 275,
        { it.lifetimeScore >= 5000 }
    ),
    GAMES_150(
        R.string.achievement_games_150_title, R.string.achievement_games_150_desc, "🎡", 275,
        { it.gamesPlayed >= 150 }
    ),
    ONLINE_WIN_25(
        R.string.achievement_online_win_25_title, R.string.achievement_online_win_25_desc, "🎗️", 275,
        { it.onlineWins >= 25 }
    ),
    WORDS_1000(
        R.string.achievement_words_1000_title, R.string.achievement_words_1000_desc, "🎨", 300,
        { it.lifetimeWordsDrawn >= 1000 }
    ),
    STREAK_21(
        R.string.achievement_streak_21_title, R.string.achievement_streak_21_desc, "🧭", 300,
        { it.currentStreak >= 21 || it.bestStreak >= 21 }
    ),
    GAMES_200(
        R.string.achievement_games_200_title, R.string.achievement_games_200_desc, "👑", 325,
        { it.gamesPlayed >= 200 }
    ),
    PERFECT_50(
        R.string.achievement_perfect_50_title, R.string.achievement_perfect_50_desc, "🧠", 325,
        { it.perfectRounds >= 50 }
    ),
    XP_25000(
        R.string.achievement_xp_25000_title, R.string.achievement_xp_25000_desc, "🌪️", 325,
        { it.lifetimeXp >= 25000 }
    ),
    STREAK_30(
        R.string.achievement_streak_30_title, R.string.achievement_streak_30_desc, "📅", 350,
        { it.currentStreak >= 30 || it.bestStreak >= 30 }
    ),
    WORDS_1500(
        R.string.achievement_words_1500_title, R.string.achievement_words_1500_desc, "🪶", 375,
        { it.lifetimeWordsDrawn >= 1500 }
    ),
    SCORE_10000(
        R.string.achievement_score_10000_title, R.string.achievement_score_10000_desc, "🪙", 400,
        { it.lifetimeScore >= 10000 }
    ),
    GAMES_300(
        R.string.achievement_games_300_title, R.string.achievement_games_300_desc, "🎢", 400,
        { it.gamesPlayed >= 300 }
    ),
    ONLINE_WIN_50(
        R.string.achievement_online_win_50_title, R.string.achievement_online_win_50_desc, "🛡️", 400,
        { it.onlineWins >= 50 }
    ),
    PERFECT_75(
        R.string.achievement_perfect_75_title, R.string.achievement_perfect_75_desc, "🔭", 400,
        { it.perfectRounds >= 75 }
    ),
    STREAK_45(
        R.string.achievement_streak_45_title, R.string.achievement_streak_45_desc, "⏳", 450,
        { it.currentStreak >= 45 || it.bestStreak >= 45 }
    ),
    PERFECT_100(
        R.string.achievement_perfect_100_title, R.string.achievement_perfect_100_desc, "🧩", 475,
        { it.perfectRounds >= 100 }
    ),
    WORDS_2500(
        R.string.achievement_words_2500_title, R.string.achievement_words_2500_desc, "🏛️", 500,
        { it.lifetimeWordsDrawn >= 2500 }
    ),
    XP_50000(
        R.string.achievement_xp_50000_title, R.string.achievement_xp_50000_desc, "🪐", 500,
        { it.lifetimeXp >= 50000 }
    ),
    SCORE_15000(
        R.string.achievement_score_15000_title, R.string.achievement_score_15000_desc, "🌟", 500,
        { it.lifetimeScore >= 15000 }
    ),
    ONLINE_WIN_75(
        R.string.achievement_online_win_75_title, R.string.achievement_online_win_75_desc, "🥊", 500,
        { it.onlineWins >= 75 }
    ),
    STREAK_60(
        R.string.achievement_streak_60_title, R.string.achievement_streak_60_desc, "🌙", 525,
        { it.currentStreak >= 60 || it.bestStreak >= 60 }
    ),
    GAMES_500(
        R.string.achievement_games_500_title, R.string.achievement_games_500_desc, "⚔️", 550,
        { it.gamesPlayed >= 500 }
    ),
    SCORE_20000(
        R.string.achievement_score_20000_title, R.string.achievement_score_20000_desc, "🔱", 575,
        { it.lifetimeScore >= 20000 }
    ),
    ONLINE_WIN_100(
        R.string.achievement_online_win_100_title, R.string.achievement_online_win_100_desc, "⚡", 600,
        { it.onlineWins >= 100 }
    ),
    PERFECT_150(
        R.string.achievement_perfect_150_title, R.string.achievement_perfect_150_desc, "🪬", 600,
        { it.perfectRounds >= 150 }
    ),
    GAMES_750(
        R.string.achievement_games_750_title, R.string.achievement_games_750_desc, "🛶", 675,
        { it.gamesPlayed >= 750 }
    ),
    STREAK_100(
        R.string.achievement_streak_100_title, R.string.achievement_streak_100_desc, "🗓️", 700,
        { it.currentStreak >= 100 || it.bestStreak >= 100 }
    ),
    WORDS_5000(
        R.string.achievement_words_5000_title, R.string.achievement_words_5000_desc, "🖼️", 725,
        { it.lifetimeWordsDrawn >= 5000 }
    ),
    XP_100000(
        R.string.achievement_xp_100000_title, R.string.achievement_xp_100000_desc, "🌞", 725,
        { it.lifetimeXp >= 100000 }
    ),
    SCORE_30000(
        R.string.achievement_score_30000_title, R.string.achievement_score_30000_desc, "💵", 725,
        { it.lifetimeScore >= 30000 }
    ),
    ONLINE_WIN_150(
        R.string.achievement_online_win_150_title, R.string.achievement_online_win_150_desc, "🦅", 750,
        { it.onlineWins >= 150 }
    ),
    GAMES_1000(
        R.string.achievement_games_1000_title, R.string.achievement_games_1000_desc, "🗡️", 800,
        { it.gamesPlayed >= 1000 }
    ),
    PERFECT_250(
        R.string.achievement_perfect_250_title, R.string.achievement_perfect_250_desc, "🌌", 800,
        { it.perfectRounds >= 250 }
    ),
    STREAK_150(
        R.string.achievement_streak_150_title, R.string.achievement_streak_150_desc, "🕰️", 875,
        { it.currentStreak >= 150 || it.bestStreak >= 150 }
    ),
    WORDS_7500(
        R.string.achievement_words_7500_title, R.string.achievement_words_7500_desc, "📚", 900,
        { it.lifetimeWordsDrawn >= 7500 }
    ),
    SCORE_50000(
        R.string.achievement_score_50000_title, R.string.achievement_score_50000_desc, "🏦", 975,
        { it.lifetimeScore >= 50000 }
    ),
    GAMES_1500(
        R.string.achievement_games_1500_title, R.string.achievement_games_1500_desc, "🚀", 1000,
        { it.gamesPlayed >= 1500 }
    ),
    ONLINE_WIN_250(
        R.string.achievement_online_win_250_title, R.string.achievement_online_win_250_desc, "👊", 1000,
        { it.onlineWins >= 250 }
    ),
    STREAK_200(
        R.string.achievement_streak_200_title, R.string.achievement_streak_200_desc, "🌗", 1050,
        { it.currentStreak >= 200 || it.bestStreak >= 200 }
    ),
    WORDS_10000(
        R.string.achievement_words_10000_title, R.string.achievement_words_10000_desc, "🏺", 1075,
        { it.lifetimeWordsDrawn >= 10000 }
    ),
    GAMES_2000(
        R.string.achievement_games_2000_title, R.string.achievement_games_2000_desc, "🏔️", 1200,
        { it.gamesPlayed >= 2000 }
    ),
    PERFECT_500(
        R.string.achievement_perfect_500_title, R.string.achievement_perfect_500_desc, "🛸", 1200,
        { it.perfectRounds >= 500 }
    ),
    XP_250000(
        R.string.achievement_xp_250000_title, R.string.achievement_xp_250000_desc, "🛰️", 1225,
        { it.lifetimeXp >= 250000 }
    ),
    SCORE_75000(
        R.string.achievement_score_75000_title, R.string.achievement_score_75000_desc, "🧿", 1225,
        { it.lifetimeScore >= 75000 }
    ),
    WORDS_15000(
        R.string.achievement_words_15000_title, R.string.achievement_words_15000_desc, "🗞️", 1350,
        { it.lifetimeWordsDrawn >= 15000 }
    ),
    SCORE_100000(
        R.string.achievement_score_100000_title, R.string.achievement_score_100000_desc, "💠", 1450,
        { it.lifetimeScore >= 100000 }
    ),
    STREAK_365(
        R.string.achievement_streak_365_title, R.string.achievement_streak_365_desc, "🎆", 1475,
        { it.currentStreak >= 365 || it.bestStreak >= 365 }
    ),
    ONLINE_WIN_500(
        R.string.achievement_online_win_500_title, R.string.achievement_online_win_500_desc, "🐉", 1500,
        { it.onlineWins >= 500 }
    ),
    PERFECT_1000(
        R.string.achievement_perfect_1000_title, R.string.achievement_perfect_1000_desc, "🧬", 1775,
        { it.perfectRounds >= 1000 }
    ),
    WORDS_25000(
        R.string.achievement_words_25000_title, R.string.achievement_words_25000_desc, "🏰", 1825,
        { it.lifetimeWordsDrawn >= 25000 }
    ),
    XP_500000(
        R.string.achievement_xp_500000_title, R.string.achievement_xp_500000_desc, "🌌", 1825,
        { it.lifetimeXp >= 500000 }
    ),
    GAMES_5000(
        R.string.achievement_games_5000_title, R.string.achievement_games_5000_desc, "🌋", 2025,
        { it.gamesPlayed >= 5000 }
    ),
    SCORE_250000(
        R.string.achievement_score_250000_title, R.string.achievement_score_250000_desc, "🪐", 2500,
        { it.lifetimeScore >= 250000 }
    ),
    WORDS_50000(
        R.string.achievement_words_50000_title, R.string.achievement_words_50000_desc, "🗿", 2750,
        { it.lifetimeWordsDrawn >= 50000 }
    ),
    XP_1000000(
        R.string.achievement_xp_1000000_title, R.string.achievement_xp_1000000_desc, "♾️", 2750,
        { it.lifetimeXp >= 1000000 }
    ),
    WORDS_100000(
        R.string.achievement_words_100000_title, R.string.achievement_words_100000_desc, "🌠", 4125,
        { it.lifetimeWordsDrawn >= 100000 }
    )
}
