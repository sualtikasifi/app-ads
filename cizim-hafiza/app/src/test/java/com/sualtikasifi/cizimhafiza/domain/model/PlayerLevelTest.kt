package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLevelTest {

    @Test
    fun `level 1 is free`() {
        assertEquals(0, PlayerLevel.totalXpForLevel(1))
        assertEquals(1, PlayerLevel.levelForXp(0))
        assertEquals(1, PlayerLevel.levelForXp(-500))
    }

    /**
     * levelForXp and totalXpForLevel are two views of the same curve — the
     * progress bar on MainMenuScreen's level badge is drawn from one and the
     * badge number from the other, so any drift between them shows up as a
     * bar that is full while the level has not changed.
     */
    @Test
    fun `levelForXp is the exact inverse of totalXpForLevel`() {
        for (level in 1..PlayerLevel.MAX_LEVEL) {
            val floor = PlayerLevel.totalXpForLevel(level)
            assertEquals("at the floor of level $level", level, PlayerLevel.levelForXp(floor))
            if (level < PlayerLevel.MAX_LEVEL) {
                assertEquals(
                    "one XP below the floor of level ${level + 1}",
                    level,
                    PlayerLevel.levelForXp(PlayerLevel.totalXpForLevel(level + 1) - 1)
                )
            }
        }
    }

    @Test
    fun `the curve only ever climbs`() {
        for (level in 2..PlayerLevel.MAX_LEVEL) {
            assertTrue(
                "level $level must cost more than level ${level - 1}",
                PlayerLevel.totalXpForLevel(level) > PlayerLevel.totalXpForLevel(level - 1)
            )
        }
    }

    @Test
    fun `XP beyond the top level does not overflow past MAX_LEVEL`() {
        assertEquals(PlayerLevel.MAX_LEVEL, PlayerLevel.levelForXp(Int.MAX_VALUE))
        assertEquals(
            PlayerLevel.MAX_LEVEL,
            PlayerLevel.levelForXp(PlayerLevel.totalXpForLevel(PlayerLevel.MAX_LEVEL) * 10)
        )
    }

    @Test
    fun `LevelProgressState reports a sane fraction across the whole ladder`() {
        for (level in 1 until PlayerLevel.MAX_LEVEL) {
            val atFloor = LevelProgressState.forXp(PlayerLevel.totalXpForLevel(level))
            assertEquals(level, atFloor.level)
            assertEquals(0, atFloor.xpIntoLevel)
            assertTrue(atFloor.progressFraction in 0f..1f)
        }
    }

    @Test
    fun `max level always reads as complete`() {
        val maxed = LevelProgressState.forXp(PlayerLevel.totalXpForLevel(PlayerLevel.MAX_LEVEL))
        assertTrue(maxed.isMaxLevel)
        assertEquals(1f, maxed.progressFraction, 0.0001f)
        assertEquals(null, maxed.nextTier)
        assertEquals(0, maxed.xpToNextTier)
    }

    @Test
    fun `every tier is reachable and ordered`() {
        var previousMin = 0
        LevelTier.entries.forEach { tier ->
            assertTrue("tiers must be in ascending minLevel order", tier.minLevel > previousMin)
            assertTrue(tier.minLevel <= PlayerLevel.MAX_LEVEL)
            assertEquals(tier, LevelTier.forLevel(tier.minLevel))
            previousMin = tier.minLevel
        }
    }
}

class XpAwardsTest {

    @Test
    fun `harder words are always worth more at the same speed`() {
        val slow = 10_000L
        assertTrue(
            XpAwards.wordXp(Difficulty.HARD, slow) > XpAwards.wordXp(Difficulty.MEDIUM, slow)
        )
        assertTrue(
            XpAwards.wordXp(Difficulty.MEDIUM, slow) > XpAwards.wordXp(Difficulty.EASY, slow)
        )
    }

    @Test
    fun `answering faster never pays less`() {
        Difficulty.entries.forEach { difficulty ->
            val times = listOf(500L, 1_999L, 2_000L, 3_999L, 4_000L, 5_999L, 6_000L, 30_000L)
            val awards = times.map { XpAwards.wordXp(difficulty, it) }
            assertEquals(
                "$difficulty: XP must be non-increasing as response time grows",
                awards.sortedDescending(),
                awards
            )
        }
    }

    @Test
    fun `word XP is always positive`() {
        Difficulty.entries.forEach { difficulty ->
            assertTrue(XpAwards.wordXp(difficulty, Long.MAX_VALUE) > 0)
        }
    }

    @Test
    fun `more stars are always worth more`() {
        assertTrue(XpAwards.levelCompletionBonus(3) > XpAwards.levelCompletionBonus(2))
        assertTrue(XpAwards.levelCompletionBonus(2) > XpAwards.levelCompletionBonus(1))
        assertEquals(0, XpAwards.levelCompletionBonus(0))
    }

    @Test
    fun `daily streak multiplier climbs by day and then holds at the cap`() {
        assertEquals(1, XpAwards.dailyStreakMultiplier(1))
        assertEquals(4, XpAwards.dailyStreakMultiplier(4))
        assertEquals(XpAwards.MAX_DAILY_STREAK_MULTIPLIER, XpAwards.dailyStreakMultiplier(XpAwards.MAX_DAILY_STREAK_MULTIPLIER))
        // Past the cap it holds rather than continuing to climb — day 37 and
        // day 400 are both worth the same 10x.
        assertEquals(XpAwards.MAX_DAILY_STREAK_MULTIPLIER, XpAwards.dailyStreakMultiplier(37))
        assertEquals(XpAwards.MAX_DAILY_STREAK_MULTIPLIER, XpAwards.dailyStreakMultiplier(400))
    }

    @Test
    fun `daily streak multiplier never decreases as the streak grows`() {
        var previous = 0
        for (day in 1..200) {
            val multiplier = XpAwards.dailyStreakMultiplier(day)
            assertTrue("day $day dropped below day ${day - 1}", multiplier >= previous)
            previous = multiplier
        }
    }

    @Test
    fun `multiplier increase is announced every day up to the cap and never after`() {
        for (day in 2..XpAwards.MAX_DAILY_STREAK_MULTIPLIER) {
            assertTrue("day $day should raise the multiplier", XpAwards.dailyStreakMultiplierJustIncreased(day))
        }
        listOf(XpAwards.MAX_DAILY_STREAK_MULTIPLIER + 1, 37, 400).forEach { day ->
            assertTrue(
                "day $day is past the cap and cannot raise the multiplier",
                !XpAwards.dailyStreakMultiplierJustIncreased(day)
            )
        }
    }

    @Test
    fun `daily total multiplies the whole payout by the streak`() {
        val base = XpAwards.DAILY_COMPLETION + 3 * XpAwards.DAILY_CORRECT_WORD
        assertEquals(base, XpAwards.dailyChallengeTotal(correctCount = 3, streakDays = 1))
        assertEquals(base * 4, XpAwards.dailyChallengeTotal(correctCount = 3, streakDays = 4))
        // Capped: a 37-day streak pays the same as a 10-day one.
        assertEquals(
            XpAwards.dailyChallengeTotal(correctCount = 3, streakDays = XpAwards.MAX_DAILY_STREAK_MULTIPLIER),
            XpAwards.dailyChallengeTotal(correctCount = 3, streakDays = 37)
        )
    }

    @Test
    fun `a zero-correct daily still pays for turning up`() {
        assertTrue(XpAwards.dailyChallengeTotal(correctCount = 0, streakDays = 1) > 0)
        // Guards against a negative count ever subtracting XP.
        assertEquals(
            XpAwards.dailyChallengeTotal(0, 1),
            XpAwards.dailyChallengeTotal(-5, 1)
        )
    }
}
