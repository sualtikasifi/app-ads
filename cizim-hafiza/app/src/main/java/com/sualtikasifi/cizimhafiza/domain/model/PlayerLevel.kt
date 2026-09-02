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
 * months. A daily-challenge-only player earns roughly 50-140 XP a day
 * depending on their streak bonus (see XpAwards) — noticeably less than the
 * curve's own early thresholds (100/250/450 XP for levels 2-4), on purpose,
 * so reaching a new level takes a session or two rather than one big combo
 * of "played the daily and a level-map level" blowing through three of
 * them at once.
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
 *
 * Every number below was roughly halved to a third of an earlier revision
 * that paid so generously a single combined session (one daily challenge +
 * one level-map level) could jump a fresh account from level 1 to level 4
 * in one sitting — [PlayerLevel]'s curve packs its early thresholds close
 * together (100/250/450/700 XP for levels 2-5) specifically so "the first
 * levels arrive within a session or two", and a single event routinely
 * paying 200-400 XP blew straight through three or four of them at once.
 * These values are sized so one generous day (daily challenge plus a
 * level) lands just past a single threshold, not several.
 */
object XpAwards {

    /** Finishing an online match, win or lose — showing up is the point. */
    const val ONLINE_MATCH = 8

    /** On top of [ONLINE_MATCH], for placing first. */
    const val ONLINE_WIN = 25

    /**
     * XP for one correctly guessed word — in a solo game, a level-map play,
     * or an online match alike (see SubmitGuessUseCase, which is shared by
     * all three). Not used by the daily challenge, which pays its own
     * flat-plus-streak reward instead (see [dailyChallengeTotal]) — granting
     * both would double-pay the same round.
     *
     * Two things push the number up, matching how a round is actually
     * played: harder words are worth more (an EASY word answered on
     * reflex teaches nothing about skill; a HARD one does), and answering
     * fast is worth more on top of that — mirrored from the existing point
     * score's speed bonus (see GameConstants.SPEED_BONUS_*), but graded
     * across three speed bands instead of one on/off threshold so getting
     * faster keeps paying off rather than capping out at "under 3
     * seconds."
     */
    fun wordXp(difficulty: Difficulty, responseTimeMs: Long): Int {
        val base = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 4
            Difficulty.HARD -> 6
        }
        val speedBonus = when {
            responseTimeMs < 2_000L -> 3
            responseTimeMs < 4_000L -> 2
            responseTimeMs < 6_000L -> 1
            else -> 0
        }
        return base + speedBonus
    }

    /**
     * One-off bonus for finishing a level-map level, scaled by the stars it
     * earned (see LevelProgressRepository.recordLevelResult) — on top of
     * the per-word XP from [wordXp], not instead of it, so a level is worth
     * more than the same words played as free-play.
     */
    fun levelCompletionBonus(stars: Int): Int = when (stars) {
        3 -> 30
        2 -> 15
        1 -> 5
        else -> 0
    }

    /** Just for completing today's challenge, however badly. */
    const val DAILY_COMPLETION = 40

    /** Per word answered correctly in the daily challenge. */
    const val DAILY_CORRECT_WORD = 10

    /**
     * The streak stops raising the multiplier here — day 10 and every day
     * after it pay the same x10. Uncapped, a year-long streak would pay 365x
     * and make every other way of earning XP irrelevant; ten days is already
     * far enough that reaching it is the achievement.
     */
    const val MAX_DAILY_STREAK_MULTIPLIER = 10

    /**
     * A streak multiplies the whole daily payout rather than adding a flat
     * bonus on top of it: day 4 of a streak pays 4x, day 10 (and every day
     * after) pays the [MAX_DAILY_STREAK_MULTIPLIER] cap. Multiplying rather
     * than adding is what makes an unbroken streak worth protecting — the
     * old tiered bonus topped out at +50 XP, small enough next to the base
     * payout that missing a day cost almost nothing.
     */
    fun dailyStreakMultiplier(streakDays: Int): Int =
        streakDays.coerceIn(1, MAX_DAILY_STREAK_MULTIPLIER)

    /**
     * True when finishing today's challenge is what raised the multiplier —
     * i.e. every day up to the cap. Used to tell the player their multiplier
     * went up, not merely that they earned one again.
     */
    fun dailyStreakMultiplierJustIncreased(streakDays: Int): Boolean =
        dailyStreakMultiplier(streakDays) > dailyStreakMultiplier(streakDays - 1)

    /** Total XP for one finished daily challenge, streak multiplier included. */
    fun dailyChallengeTotal(correctCount: Int, streakDays: Int): Int =
        (DAILY_COMPLETION + correctCount.coerceAtLeast(0) * DAILY_CORRECT_WORD) *
            dailyStreakMultiplier(streakDays)
}
