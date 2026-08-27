package com.sualtikasifi.cizimhafiza.domain.model

/**
 * The player's single progression ladder, driven by experience points (see
 * SettingsRepository.lifetimeXp).
 *
 * There is deliberately only one currency here. [PlayerRank] used to be
 * computed straight from `lifetimeScore` — raw game points — which meant a
 * player could see their score climb while nothing else moved, and it gave
 * the daily challenge no way to reward *showing up* rather than scoring
 * well. XP fixes that: points still feed it, but so do daily challenges,
 * streaks and achievements, and the rank tiers are now just the visual
 * bands of this one ladder ([LevelTier]).
 *
 * The curve is quadratic — roughly `25n² + 75n` total XP to reach level n —
 * so the first levels arrive within a session or two and the last ones take
 * months. A daily-challenge-only player earns ~200-300 XP a day, which puts
 * level 20 about a month out and level 40 several months out.
 */
object PlayerLevel {

    const val MAX_LEVEL = 100

    /** Total XP needed to have *reached* [level]. Level 1 is the floor, so it costs nothing. */
    fun totalXpForLevel(level: Int): Int {
        val n = (level - 1).coerceAtLeast(0)
        return 25 * n * n + 75 * n
    }

    /** The level [totalXp] buys, capped at [MAX_LEVEL]. */
    fun levelForXp(totalXp: Int): Int {
        if (totalXp <= 0) return 1
        // Closed form beats a loop here only marginally, and the loop stays
        // obviously correct against totalXpForLevel — which is the function
        // the UI's progress bar is drawn from, so the two must not drift.
        var level = 1
        while (level < MAX_LEVEL && totalXp >= totalXpForLevel(level + 1)) level++
        return level
    }
}

/**
 * The visual band a level falls into — what the player actually *sees* on
 * another player's avatar (see presentation.common.LevelAvatar), and the
 * thing that makes a level feel like it changed something rather than just
 * incrementing a number.
 *
 * Named after the existing art-themed ranks so the titles players already
 * know carry over; [minLevel] bands are 20 levels wide, which lines up with
 * the "0-20 starter, 20-40 a bit better, then glowing/flaming" progression.
 */
enum class LevelTier(val minLevel: Int, val rank: PlayerRank) {
    SCRIBBLER(1, PlayerRank.KARALAMACI),
    APPRENTICE(20, PlayerRank.CIRAK),
    PAINTER(40, PlayerRank.RESSAM),
    MASTER_PAINTER(60, PlayerRank.USTA_RESSAM),
    ARTIST(80, PlayerRank.SANATCI),
    GRAND_MASTER(PlayerLevel.MAX_LEVEL, PlayerRank.BUYUK_USTA);

    companion object {
        fun forLevel(level: Int): LevelTier = entries.last { level >= it.minLevel }
    }
}

/**
 * Everything the UI needs to draw the player's standing without recomputing
 * any of it — the level badge, its tier, and how far along the current level
 * the player is.
 */
data class LevelProgressState(
    val totalXp: Int,
    val level: Int,
    val tier: LevelTier,
    /** XP earned inside the current level (0 until the next level's cost). */
    val xpIntoLevel: Int,
    /** XP the current level costs in total; 0 once [level] is [PlayerLevel.MAX_LEVEL]. */
    val xpForThisLevel: Int
) {
    val isMaxLevel: Boolean get() = level >= PlayerLevel.MAX_LEVEL

    /** The next visual band up, or null once the player is in the top one. */
    val nextTier: LevelTier? get() = LevelTier.entries.getOrNull(tier.ordinal + 1)

    /** XP still to earn before [nextTier] is reached; 0 in the top band. */
    val xpToNextTier: Int
        get() = nextTier?.let { (PlayerLevel.totalXpForLevel(it.minLevel) - totalXp).coerceAtLeast(0) } ?: 0

    /** 0f..1f across the current level; always 1f at max level. */
    val progressFraction: Float
        get() = if (xpForThisLevel <= 0) 1f else (xpIntoLevel.toFloat() / xpForThisLevel).coerceIn(0f, 1f)

    companion object {
        fun forXp(totalXp: Int): LevelProgressState {
            val safeXp = totalXp.coerceAtLeast(0)
            val level = PlayerLevel.levelForXp(safeXp)
            val floor = PlayerLevel.totalXpForLevel(level)
            val ceiling = if (level >= PlayerLevel.MAX_LEVEL) floor else PlayerLevel.totalXpForLevel(level + 1)
            return LevelProgressState(
                totalXp = safeXp,
                level = level,
                tier = LevelTier.forLevel(level),
                xpIntoLevel = safeXp - floor,
                xpForThisLevel = ceiling - floor
            )
        }
    }
}

/**
 * What each thing a player can do is worth. Kept in one place so the
 * economy can be retuned without hunting through call sites.
 *
 * The daily numbers dominate on purpose: the whole point of the daily
 * challenge is that turning up every day out-earns grinding solo games in
 * one sitting, and that a long streak is worth protecting.
 */
object XpAwards {

    /** Per correct word in any ordinary solo game. */
    const val SOLO_CORRECT_WORD = 3

    /** Finishing an online match, win or lose — showing up is the point. */
    const val ONLINE_MATCH = 15

    /** On top of [ONLINE_MATCH], for placing first. */
    const val ONLINE_WIN = 50

    /** Unlocking any achievement. */
    const val ACHIEVEMENT = 150

    /** Just for completing today's challenge, however badly. */
    const val DAILY_COMPLETION = 100

    /** Per word answered correctly in the daily challenge. */
    const val DAILY_CORRECT_WORD = 20

    /** Every daily-streak day is worth this much, up to [DAILY_STREAK_BONUS_CAP_DAYS]. */
    const val DAILY_STREAK_DAY = 10
    const val DAILY_STREAK_BONUS_CAP_DAYS = 30

    /** The streak bonus paid out for finishing a daily challenge on day [streakDays]. */
    fun dailyStreakBonus(streakDays: Int): Int =
        streakDays.coerceIn(0, DAILY_STREAK_BONUS_CAP_DAYS) * DAILY_STREAK_DAY

    /** Total XP for one finished daily challenge. */
    fun dailyChallengeTotal(correctCount: Int, streakDays: Int): Int =
        DAILY_COMPLETION + correctCount.coerceAtLeast(0) * DAILY_CORRECT_WORD + dailyStreakBonus(streakDays)
}
