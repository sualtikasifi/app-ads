package com.sualtikasifi.cizimhafiza.domain.model

data class LevelProgress(
    val worldId: Int,
    val levelIndex: Int,
    val bestStars: Int,
    val bestScore: Int,
    val lastPlayedEpochMillis: Long
)

/** Purely a motivational/replay-value display — never gates progression (see LevelCatalog). */
object LevelStars {
    fun forAccuracy(correctCount: Int, totalWords: Int): Int {
        if (totalWords == 0) return 0
        val ratio = correctCount.toFloat() / totalWords
        return when {
            ratio >= 0.85f -> 3
            ratio >= 0.60f -> 2
            ratio >= 0.30f -> 1
            else -> 0
        }
    }
}
