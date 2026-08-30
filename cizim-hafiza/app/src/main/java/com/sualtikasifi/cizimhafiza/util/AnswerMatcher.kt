package com.sualtikasifi.cizimhafiza.util

import java.text.Normalizer

/**
 * Compares a user's typed guess against the target word with Turkish-aware
 * normalization and a small Levenshtein tolerance, so things like case,
 * extra whitespace, or a missing dotless-i still count as correct.
 */
object AnswerMatcher {

    /**
     * How many single-character edits a guess may be away from the target and
     * still count, **scaled to the target's own length**.
     *
     * A flat tolerance is only safe on long words. At a flat 2 — which this
     * used to be — a third of the Turkish pool became mutually
     * interchangeable: `gül`/`gol` are 1 apart, `top`/`gol` and `kale`/`file`
     * are 2 apart, and every one of those pairs is a real, separate word in
     * words.json. A player could type a completely unrelated word and score.
     * Two letters of slack simply cannot be spent on a three-letter word, so
     * short words get none: they are too short to typo-protect without
     * colliding with their neighbours.
     */
    fun toleranceFor(target: String): Int = when (normalize(target).length) {
        in 0..4 -> 0
        in 5..7 -> 1
        else -> 2
    }

    /** Convenience overload that derives the tolerance from [target] itself — see [toleranceFor]. */
    fun isCorrect(userAnswer: String, target: String): Boolean =
        isCorrect(userAnswer, target, toleranceFor(target))

    fun isCorrect(userAnswer: String, target: String, tolerance: Int): Boolean {
        val a = normalize(userAnswer)
        val b = normalize(target)
        if (a.isEmpty()) return false
        if (a == b) return true
        return levenshtein(a, b) <= tolerance
    }

    fun normalize(input: String): String {
        val trimmed = input.trim().lowercase(TR_LOCALE)
        // Fold Turkish-specific letters to their ASCII equivalents so that
        // keyboard/locale differences (ı vs i, ş vs s, ç vs c...) don't
        // cause false negatives.
        val folded = trimmed
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
        val deAccented = Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return deAccented.replace(Regex("\\s+"), " ").trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousRow = IntArray(b.length + 1) { it }
        var currentRow = IntArray(b.length + 1)

        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,
                    previousRow[j] + 1,
                    previousRow[j - 1] + cost
                )
            }
            val tmp = previousRow
            previousRow = currentRow
            currentRow = tmp
        }
        return previousRow[b.length]
    }

    private val TR_LOCALE = java.util.Locale.forLanguageTag("tr-TR")
}
