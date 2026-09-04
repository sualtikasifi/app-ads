package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions that shape the opponent pool: which rounds get left
 * behind, and which band they land in. Both are pure, and both are easy to
 * get subtly wrong in a way nothing would notice until players started
 * being matched against rounds they should never have met.
 */
class GhostRunsTest {

    private fun worthRecording(
        mode: GameMode = GameMode.NORMAL,
        isLevelRound: Boolean = false,
        isDailyChallenge: Boolean = false,
        correctCount: Int = GhostRuns.MIN_CORRECT
    ) = GhostRuns.isWorthRecording(mode, isLevelRound, isDailyChallenge, correctCount)

    @Test
    fun `an ordinary free-play round is recorded`() {
        assertTrue(worthRecording())
    }

    @Test
    fun `a relaxed round is never recorded`() {
        // No countdown at all, so its score cannot be set against a timed one.
        assertFalse(worthRecording(mode = GameMode.RELAXED))
    }

    @Test
    fun `a level round is never recorded`() {
        assertFalse(worthRecording(isLevelRound = true))
    }

    @Test
    fun `the daily challenge is never recorded`() {
        // Everyone plays the same words that day; matching against one would
        // replay the round the challenger just finished.
        assertFalse(worthRecording(isDailyChallenge = true))
    }

    @Test
    fun `a round below the quality bar is not recorded`() {
        assertFalse(worthRecording(correctCount = GhostRuns.MIN_CORRECT - 1))
        assertFalse(worthRecording(correctCount = 0))
    }

    @Test
    fun `the quality bar is inclusive`() {
        assertTrue(worthRecording(correctCount = GhostRuns.MIN_CORRECT))
    }

    @Test
    fun `levels one to ten share the first band`() {
        // The band is an equality filter, so an off-by-one here would split
        // beginners across two pools and halve their opponents.
        assertEquals(0, GhostRuns.levelBandFor(1))
        assertEquals(0, GhostRuns.levelBandFor(10))
        assertEquals(1, GhostRuns.levelBandFor(11))
        assertEquals(1, GhostRuns.levelBandFor(20))
        assertEquals(2, GhostRuns.levelBandFor(21))
    }

    @Test
    fun `bands stay non-negative for nonsense levels`() {
        assertEquals(0, GhostRuns.levelBandFor(0))
        assertEquals(0, GhostRuns.levelBandFor(-5))
    }

    @Test
    fun `the top level lands in a band of its own`() {
        assertEquals(9, GhostRuns.levelBandFor(PlayerLevel.MAX_LEVEL))
    }
}
