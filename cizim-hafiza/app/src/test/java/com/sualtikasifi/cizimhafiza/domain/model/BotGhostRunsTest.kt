package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sude's fallback round has one invariant holding it together: the offer
 * screen and the result screen derive her score independently, from nothing
 * but the run id, and must land on the same answer. If they ever drift, a
 * player is shown one score before the match and beaten by a different one
 * after it — with nothing in the code obviously wrong.
 */
class BotGhostRunsTest {

    private val words = listOf(4, 17, 23, 41, 58, 66)

    @Test
    fun `an id round-trips its seed and words`() {
        val id = BotGhostRuns.idFor(seed = -8_123_456_789L, wordIds = words)
        val parsed = BotGhostRuns.parse(id)
        assertNotNull(parsed)
        assertEquals(-8_123_456_789L, parsed!!.first)
        assertEquals(words, parsed.second)
    }

    @Test
    fun `a real opponent's id is not mistaken for one of hers`() {
        // Firestore's own generated ids are what actually flow through here.
        assertFalse(BotGhostRuns.isBotRun("HqL3vZ0k9mNbYtR2sXWd"))
        assertNull(BotGhostRuns.parse("HqL3vZ0k9mNbYtR2sXWd"))
    }

    @Test
    fun `a malformed id is rejected rather than half-read`() {
        assertNull(BotGhostRuns.parse("sude:notanumber:1,2,3"))
        assertNull(BotGhostRuns.parse("sude:42:1,two,3"))
        assertNull(BotGhostRuns.parse("sude:42"))
    }

    @Test
    fun `the same id always scores the same`() {
        // The offer and the result screen never speak to each other, so this
        // is the only thing keeping them consistent.
        val first = BotGhostRuns.outcomeFor(seed = 99L, wordIds = words)
        val second = BotGhostRuns.outcomeFor(seed = 99L, wordIds = words)
        assertEquals(first.correctness, second.correctness)
        assertEquals(first.totalScore, second.totalScore)
        assertEquals(first.correctCount, second.correctCount)
        assertEquals(first.fastestCorrectMs, second.fastestCorrectMs)
    }

    @Test
    fun `the score always describes the correctness beside it`() {
        // The result gallery marks each drawing from `correctness` while the
        // scoreboard shows `correctCount` — two renderings of one round.
        repeat(200) { seed ->
            val outcome = BotGhostRuns.outcomeFor(seed.toLong(), words)
            assertEquals(words.size, outcome.correctness.size)
            assertEquals(outcome.correctness.count { it }, outcome.correctCount)
        }
    }

    @Test
    fun `she is beatable far more often than not`() {
        // The reason her distribution is not BotRoomEngine's: over six words
        // that one sweeps the round 40% of the time, which is most of a
        // beginner's first matches lost to a perfect stranger.
        val perfect = (0 until 1_000).count {
            BotGhostRuns.outcomeFor(it.toLong(), words).correctCount == words.size
        }
        assertTrue("perfect rounds: $perfect", perfect < 250)
    }

    @Test
    fun `the same seed always names the same opponent`() {
        // The name travels with the run rather than being re-derived, so a
        // mismatch would not break anything today — but everything else
        // about a run is reproducible from its id, and the moment that stops
        // being true of one field is the moment it stops being a rule.
        assertEquals(GhostPersonas.nicknameFor(4_242L), GhostPersonas.nicknameFor(4_242L))
        assertEquals(
            GhostPersonas.levelFor(4_242L, 12, correctCount = 7, wordCount = 10),
            GhostPersonas.levelFor(4_242L, 12, correctCount = 7, wordCount = 10)
        )
    }

    @Test
    fun `the roster is drawn from evenly`() {
        // The roster is a fixed hand-written list now, so 500 draws cannot be
        // 500 distinct names — but they should cover very nearly all of it.
        // Anything less means nicknameFor is clustering on part of the list,
        // which is how a pool starts offering the same handful of people.
        val draws = (0 until 500).map { GhostPersonas.nicknameFor(it.toLong()) }
        val distinct = draws.toSet()
        assertTrue("distinct names: ${distinct.size}", distinct.size >= 190)

        // And no single name may dominate. Uniform over 200 names, 500 draws
        // put about 2.5 on each; a name turning up ten times would mean the
        // seed is barely reaching the index.
        val worst = draws.groupingBy { it }.eachCount().maxOf { it.value }
        assertTrue("most repeated name appeared $worst times", worst <= 10)
    }

    @Test
    fun `an opponent's level stays near the challenger's`() {
        // "Near" now means near the band the round earned, not near the
        // challenger flat: a middling round is still a neighbour, and the
        // bonus is what carries a good one away. Six either side is the
        // random spread — anything wider would mean the anchor slipped.
        val bands = mapOf(10 to 30, 9 to 10, 8 to 5, 6 to 0, 4 to -4, 1 to -8)
        (1..PlayerLevel.MAX_LEVEL step 7).forEach { challenger ->
            bands.forEach { (correct, bonus) ->
                (0 until 50).forEach { seed ->
                    val level = GhostPersonas.levelFor(seed.toLong(), challenger, correct, 10)
                    assertTrue("level $level for challenger $challenger", level in 1..PlayerLevel.MAX_LEVEL)
                    assertTrue(
                        "level $level too far from ${challenger + bonus} ($correct/10)",
                        kotlin.math.abs(level - (challenger + bonus)) <= 6 ||
                            level == 1 || level == PlayerLevel.MAX_LEVEL
                    )
                }
            }
        }
    }

    @Test
    fun `a better round means a higher opponent`() {
        // Averaged over the spread, so this is about the bands rather than
        // one lucky seed. A perfect stranger must not read as a beginner.
        fun mean(correct: Int) = (0 until 400)
            .map { GhostPersonas.levelFor(it.toLong(), 40, correct, 10) }
            .average()

        val perfect = mean(10)
        val good = mean(9)
        val fair = mean(8)
        val poor = mean(3)
        assertTrue("perfect $perfect vs good $good", perfect > good + 15)
        assertTrue("good $good vs fair $fair", good > fair + 2)
        assertTrue("fair $fair vs poor $poor", fair > poor + 5)
    }

    @Test
    fun `a round she got nothing right in has no fastest time`() {
        // Guards the one combination that can produce a nonsense "best time"
        // — there is no fastest correct answer when there was no correct one.
        val allWrong = (0 until 2_000)
            .map { BotGhostRuns.outcomeFor(it.toLong(), listOf(7, 9)) }
            .filter { it.correctCount == 0 }
        assertTrue("expected some all-wrong rounds", allWrong.isNotEmpty())
        allWrong.forEach { assertNull(it.fastestCorrectMs) }
    }
}
