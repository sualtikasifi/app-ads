package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three decisions that shape the opponent pool: which rounds get left
 * behind, which [GhostRuns.RUN_WORD_COUNT] words of them, and which band
 * they land in. All pure, and all easy to get subtly wrong in a way nothing
 * would notice until players started being matched against rounds they
 * should never have met — or, worse, against scores nobody could beat.
 */
class GhostRunsTest {

    private fun worthRecording(
        mode: GameMode = GameMode.NORMAL,
        isDailyChallenge: Boolean = false
    ) = GhostRuns.isWorthRecording(mode, isDailyChallenge)

    /** [correctFor] says which indexes were guessed right; the rest were not. */
    private fun run(size: Int, correctFor: Set<Int>, points: Int = 5): Triple<List<Int>, List<GhostRunWord>, List<ResultItem>> {
        val wordIds = (0 until size).toList()
        val perWord = wordIds.map {
            GhostRunWord(
                wordId = it,
                isCorrect = it in correctFor,
                // Descending, so the fastest correct answer is never simply
                // the first one in the list.
                responseTimeMs = (size - it) * 100L,
                pointsAwarded = if (it in correctFor) points else 0
            )
        }
        val items = wordIds.map { ResultItem("w$it", it in correctFor, emptyList()) }
        return Triple(wordIds, perWord, items)
    }

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
    fun `a level round is recorded`() {
        // Levels draw randomly from the same pool free play does, so there is
        // nothing level-specific in the word set — and levels are where most
        // rounds actually get played, so this is what keeps the pool filled.
        assertTrue(worthRecording(mode = GameMode.NORMAL))
    }

    @Test
    fun `the daily challenge is never recorded`() {
        // Everyone plays the same words that day; matching against one would
        // replay the round the challenger just finished.
        assertFalse(worthRecording(isDailyChallenge = true))
    }

    @Test
    fun `a round of exactly RUN_WORD_COUNT words is stored whole`() {
        val size = GhostRuns.RUN_WORD_COUNT
        val correctCount = (size - 1).coerceAtLeast(GhostRuns.MIN_CORRECT)
        val (ids, perWord, items) = run(size = size, correctFor = (0 until correctCount).toSet())
        val slice = GhostRuns.recordableSlice(ids, perWord, items)
        assertNotNull(slice)
        assertEquals(size, slice!!.wordIds.size)
        assertEquals(correctCount, slice.correctCount)
        assertEquals(correctCount * 5, slice.totalScore)
    }

    @Test
    fun `a longer round is cut to its first RUN_WORD_COUNT words`() {
        // The whole point of the cut: this fifty-word round scored 250, but
        // only the first RUN_WORD_COUNT words actually get stored. Recording
        // the round's own total would leave behind an opponent no
        // RUN_WORD_COUNT-word match could beat.
        val (ids, perWord, items) = run(size = 50, correctFor = (0 until 50).toSet())
        val slice = GhostRuns.recordableSlice(ids, perWord, items)!!
        assertEquals(GhostRuns.RUN_WORD_COUNT, slice.wordIds.size)
        assertEquals(GhostRuns.RUN_WORD_COUNT, slice.perWord.size)
        assertEquals(GhostRuns.RUN_WORD_COUNT, slice.items.size)
        assertEquals((0 until GhostRuns.RUN_WORD_COUNT).toList(), slice.wordIds)
        assertEquals(GhostRuns.RUN_WORD_COUNT, slice.correctCount)
        assertEquals(GhostRuns.RUN_WORD_COUNT * 5, slice.totalScore)
    }

    @Test
    fun `the fastest time is taken from the stored words only`() {
        // Word 40 was the fastest answer of the round, but it is not among
        // the first RUN_WORD_COUNT being stored, so it cannot be the stored
        // run's best time.
        val (ids, perWord, items) = run(size = 50, correctFor = (0 until 50).toSet())
        val slice = GhostRuns.recordableSlice(ids, perWord, items)!!
        assertEquals((50 - (GhostRuns.RUN_WORD_COUNT - 1)) * 100L, slice.fastestCorrectMs)
    }

    @Test
    fun `the quality bar is judged on the stored words`() {
        // Only the last word of the round was inside the first
        // RUN_WORD_COUNT — as an opponent this is the dispiriting round the
        // bar exists to keep out, whatever the full round's total says.
        val (ids, perWord, items) = run(
            size = GhostRuns.RUN_WORD_COUNT + 10,
            correctFor = setOf(0) + (GhostRuns.RUN_WORD_COUNT + 2..GhostRuns.RUN_WORD_COUNT + 9).toSet()
        )
        assertNull(GhostRuns.recordableSlice(ids, perWord, items))
    }

    @Test
    fun `the quality bar is inclusive`() {
        val size = GhostRuns.RUN_WORD_COUNT
        val (ids, perWord, items) = run(size = size, correctFor = setOf(0, size - 1))
        assertNotNull(GhostRuns.recordableSlice(ids, perWord, items))
    }

    @Test
    fun `a round with nothing correct is not recorded`() {
        val (ids, perWord, items) = run(size = GhostRuns.RUN_WORD_COUNT, correctFor = emptySet())
        assertNull(GhostRuns.recordableSlice(ids, perWord, items))
    }

    @Test
    fun `a round too short to fill a run is not recorded`() {
        val size = GhostRuns.RUN_WORD_COUNT - 1
        val (ids, perWord, items) = run(size = size, correctFor = (0 until size).toSet())
        assertNull(GhostRuns.recordableSlice(ids, perWord, items))
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
