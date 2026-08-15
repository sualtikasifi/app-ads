package com.sualtikasifi.cizimhafiza.util

import java.text.Normalizer

/**
 * Compares a user's typed guess against the target word with Turkish-aware
 * normalization and a small Levenshtein tolerance, so things like case,
 * extra whitespace, or a missing dotless-i still count as correct.
 */
object AnswerMatcher {

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
