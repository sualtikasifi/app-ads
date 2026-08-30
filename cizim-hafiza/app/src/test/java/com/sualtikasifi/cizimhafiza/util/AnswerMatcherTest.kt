package com.sualtikasifi.cizimhafiza.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {

    // --- normalization ---

    @Test
    fun `folds Turkish letters to ASCII`() {
        assertEquals("gul", AnswerMatcher.normalize("GÜL"))
        assertEquals("cigdem", AnswerMatcher.normalize("Çiğdem"))
        assertEquals("isik", AnswerMatcher.normalize("IŞIK"))
        assertEquals("simsek", AnswerMatcher.normalize("şimşek"))
    }

    @Test
    fun `collapses surrounding and repeated whitespace`() {
        assertEquals("deniz yildizi", AnswerMatcher.normalize("  Deniz   Yıldızı  "))
    }

    @Test
    fun `dotted and dotless i both fold to plain i`() {
        // The single most common Turkish keyboard mismatch: a player on an
        // English layout types "kirmizi" for "kırmızı".
        assertEquals(AnswerMatcher.normalize("kırmızı"), AnswerMatcher.normalize("kirmizi"))
        assertEquals(AnswerMatcher.normalize("İstanbul"), AnswerMatcher.normalize("istanbul"))
    }

    // --- length-scaled tolerance (regression cover for the flat-2 bug) ---

    @Test
    fun `tolerance scales with target length`() {
        assertEquals(0, AnswerMatcher.toleranceFor("top"))
        assertEquals(0, AnswerMatcher.toleranceFor("kale"))
        assertEquals(1, AnswerMatcher.toleranceFor("kelebek"))
        assertEquals(2, AnswerMatcher.toleranceFor("buzdolabı"))
    }

    /**
     * These exact pairs are all real, separate entries in words.json that a
     * flat tolerance of 2 accepted for one another — typing "gol" scored the
     * word "gül". Guarding them by name because a future tuning change to
     * [AnswerMatcher.toleranceFor] must not reintroduce it.
     */
    @Test
    fun `unrelated short words never match each other`() {
        assertFalse(AnswerMatcher.isCorrect("gol", "gül"))
        assertFalse(AnswerMatcher.isCorrect("gol", "top"))
        assertFalse(AnswerMatcher.isCorrect("file", "kale"))
        assertFalse(AnswerMatcher.isCorrect("kar", "zar"))
        assertFalse(AnswerMatcher.isCorrect("ege", "oje"))
    }

    @Test
    fun `short words still accept an exact answer`() {
        assertTrue(AnswerMatcher.isCorrect("gül", "gül"))
        assertTrue(AnswerMatcher.isCorrect("GUL", "gül"))
        assertTrue(AnswerMatcher.isCorrect(" top ", "top"))
    }

    @Test
    fun `long words still forgive a typo`() {
        assertTrue(AnswerMatcher.isCorrect("kelebeg", "kelebek"))
        assertTrue(AnswerMatcher.isCorrect("buzdolabi", "buzdolabı"))
        assertTrue(AnswerMatcher.isCorrect("buzdolap", "buzdolabı"))
    }

    @Test
    fun `a long word is still not a different long word`() {
        assertFalse(AnswerMatcher.isCorrect("kelebek", "bebek"))
        assertFalse(AnswerMatcher.isCorrect("kalem", "kaleci"))
    }

    @Test
    fun `blank answer is never correct`() {
        assertFalse(AnswerMatcher.isCorrect("", "top"))
        assertFalse(AnswerMatcher.isCorrect("   ", "top"))
        // A timeout submits "" — it must not match a word that normalizes to
        // nothing either.
        assertFalse(AnswerMatcher.isCorrect("", ""))
    }

    @Test
    fun `explicit tolerance overload still honours its argument`() {
        assertTrue(AnswerMatcher.isCorrect("gol", "gül", tolerance = 2))
        assertFalse(AnswerMatcher.isCorrect("gol", "gül", tolerance = 0))
    }
}
