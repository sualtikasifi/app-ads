package com.sualtikasifi.cizimhafiza.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [DailyChallengeState.streakIfCompletedToday] — the number the home
 * card promises before the player commits to a round, and therefore the
 * number the XP multiplier is quoted from.
 *
 * It changed meaning when banked "streak freezes" were removed: nothing
 * bridges a gap on its own any more, so a player coming back after a missed
 * day now starts at 1 unless they deliberately buy the streak back with an
 * ad (which backdates the completion day and is invisible from here).
 * Getting this wrong overstates the reward on the card and then pays less.
 */
class DailyStreakStateTest {

    private fun state(lastCompleted: Long, streak: Int, today: Long = 20_000L) = DailyChallengeState(
        todayEpochDay = today,
        lastCompletedEpochDay = lastCompleted,
        currentStreak = streak,
        bestStreak = streak,
        lastResult = null
    )

    @Test
    fun `playing the day after keeps the streak going`() {
        assertEquals(8, state(lastCompleted = 19_999L, streak = 7).streakIfCompletedToday)
    }

    @Test
    fun `a single missed day restarts the streak`() {
        // 19_998 is the day before yesterday: yesterday was missed, and with
        // no freezes left in the model there is nothing to bridge it.
        assertEquals(1, state(lastCompleted = 19_998L, streak = 30).streakIfCompletedToday)
    }

    @Test
    fun `a long absence restarts the streak`() {
        assertEquals(1, state(lastCompleted = 19_900L, streak = 60).streakIfCompletedToday)
    }

    @Test
    fun `the very first play starts at one`() {
        assertEquals(1, state(lastCompleted = -1L, streak = 0).streakIfCompletedToday)
    }

    @Test
    fun `already played today reports the streak as it stands`() {
        val played = state(lastCompleted = 20_000L, streak = 5)
        assertEquals(5, played.streakIfCompletedToday)
    }

    @Test
    fun `a rescued streak continues, because the rescue backdated the completion`() {
        // rescueStreak() sets lastCompleted to yesterday, so from here the
        // rescued case is indistinguishable from having actually played.
        assertEquals(31, state(lastCompleted = 19_999L, streak = 30).streakIfCompletedToday)
    }
}
