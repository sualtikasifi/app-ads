package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily challenge has exactly two non-negotiable properties (see the
 * KDoc on [DailyChallenge]): every device must derive the *same* round for a
 * given day, and it must do so offline from the date alone. Both are
 * invisible in normal play — a regression here only shows up as two friends
 * arguing about different words — so they are pinned down here instead.
 */
class DailyChallengeTest {

    private fun pool(size: Int = 300): List<Word> = (1..size).map { id ->
        Word(
            id = id,
            text = "word$id",
            category = "test",
            difficulty = when (id % 3) {
                0 -> Difficulty.EASY
                1 -> Difficulty.MEDIUM
                else -> Difficulty.HARD
            }
        )
    }

    @Test
    fun `the same day always yields the same words`() {
        val pool = pool()
        repeat(5) {
            assertEquals(
                DailyChallenge.wordsFor(20_000L, "tr", pool).map { it.id },
                DailyChallenge.wordsFor(20_000L, "tr", pool).map { it.id }
            )
        }
    }

    @Test
    fun `caller ordering of the pool cannot change the result`() {
        // The pool arrives from SQL, whose ordering depends on collation and
        // locale; selection sorts by id internally so that cannot leak in.
        val ordered = pool()
        val shuffled = ordered.shuffled()
        val reversed = ordered.reversed()
        val expected = DailyChallenge.wordsFor(20_000L, "tr", ordered).map { it.id }
        assertEquals(expected, DailyChallenge.wordsFor(20_000L, "tr", shuffled).map { it.id })
        assertEquals(expected, DailyChallenge.wordsFor(20_000L, "tr", reversed).map { it.id })
    }

    @Test
    fun `different days yield different rounds`() {
        val pool = pool()
        val today = DailyChallenge.wordsFor(20_000L, "tr", pool).map { it.id }
        val tomorrow = DailyChallenge.wordsFor(20_001L, "tr", pool).map { it.id }
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `the two language pools are drawn independently`() {
        val pool = pool()
        assertNotEquals(
            DailyChallenge.wordsFor(20_000L, "tr", pool).map { it.id },
            DailyChallenge.wordsFor(20_000L, "en", pool).map { it.id }
        )
    }

    @Test
    fun `a full round is always served and never repeats a word`() {
        val pool = pool()
        for (day in 19_000L..19_120L) {
            val words = DailyChallenge.wordsFor(day, "tr", pool)
            assertEquals("day $day", DailyChallenge.WORD_COUNT, words.size)
            assertEquals("day $day repeated a word", words.size, words.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `a thin pool still serves whatever it can without crashing`() {
        assertTrue(DailyChallenge.wordsFor(20_000L, "tr", emptyList()).isEmpty())
        val tiny = pool(size = 3)
        val words = DailyChallenge.wordsFor(20_000L, "tr", tiny)
        assertTrue(words.size <= DailyChallenge.WORD_COUNT)
        assertEquals(words.size, words.map { it.id }.toSet().size)
    }

    @Test
    fun `a device with a pre-1970 clock does not crash`() {
        // epochDay goes negative on a badly-set clock; the selection maths
        // uses floorDiv/floorMod specifically to survive that.
        val pool = pool()
        listOf(-1L, -365L, -20_000L).forEach { day ->
            val words = DailyChallenge.wordsFor(day, "tr", pool)
            assertEquals("day $day", DailyChallenge.WORD_COUNT, words.size)
        }
    }

    @Test
    fun `consecutive days do not immediately reuse a word`() {
        // With a deck-walk selection, yesterday's word turning up again today
        // reads as a bug to the player even though it is statistically fine.
        val pool = pool()
        var previous = DailyChallenge.wordsFor(19_000L, "tr", pool).map { it.id }.toSet()
        for (day in 19_001L..19_060L) {
            val today = DailyChallenge.wordsFor(day, "tr", pool).map { it.id }.toSet()
            assertTrue(
                "day $day reused a word from day ${day - 1}",
                today.intersect(previous).isEmpty()
            )
            previous = today
        }
    }
}
