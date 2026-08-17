package com.sualtikasifi.cizimhafiza.domain.model

/**
 * Turns "no specific difficulty chosen" (solo/online "Tümü") or a level
 * map's mixed step into a concrete per-difficulty word count, instead of
 * pure uniform random — a round should have a predictable difficulty
 * curve, not risk landing all-EASY or all-HARD purely by chance.
 */
object DifficultyMix {
    // "Tümü" ratio: 4 easy / 3 medium / 3 hard out of every 10 words.
    private const val EASY_SHARE = 0.4
    private const val MEDIUM_SHARE = 0.3
    private const val HARD_SHARE = 0.3

    fun allDifficulties(total: Int): Map<Difficulty, Int> = split(
        total,
        Difficulty.EASY to EASY_SHARE,
        Difficulty.MEDIUM to MEDIUM_SHARE,
        Difficulty.HARD to HARD_SHARE
    )

    /** Even split between two adjacent difficulties — used by the level map's "mixed" steps. */
    fun evenSplit(a: Difficulty, b: Difficulty, total: Int): Map<Difficulty, Int> =
        split(total, a to 0.5, b to 0.5)

    private fun split(total: Int, vararg shares: Pair<Difficulty, Double>): Map<Difficulty, Int> {
        val floors = shares.associate { (d, share) -> d to (total * share).toInt() }
        var remainder = total - floors.values.sum()
        // Largest-remainder method: whichever share's fractional part was
        // biggest gets the leftover word(s) first, so counts always sum to `total`.
        val byRemainder = shares.sortedByDescending { (d, share) -> total * share - floors.getValue(d) }
        val result = floors.toMutableMap()
        for ((d, _) in byRemainder) {
            if (remainder <= 0) break
            result[d] = result.getValue(d) + 1
            remainder--
        }
        return result
    }
}
