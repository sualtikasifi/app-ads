package com.sualtikasifi.cizimhafiza.domain.model

/** A single level's word-selection parameters — fed straight into GetWordsForGameUseCase. */
data class LevelConfig(val category: String, val difficultyMix: Map<Difficulty, Int>) {
    val wordCount: Int get() = difficultyMix.values.sum()
}

/**
 * Pure level/world math for the "Bölümler" level map — no hand-authored
 * per-level data. A level's content is entirely derived from its world's
 * category and its position (1-10) within that world: three pure-EASY
 * steps, two EASY/MEDIUM-mixed steps, two pure-MEDIUM steps, two
 * MEDIUM/HARD-mixed steps, and a final pure-HARD step — a gradual curve
 * rather than an abrupt EASY→MEDIUM→HARD jump every three levels.
 */
object LevelCatalog {
    const val LEVELS_PER_WORLD = 10

    // Below the smallest single-difficulty word pool, so no level is ever
    // short on words regardless of which world/difficulty(-mix) it uses.
    const val WORDS_PER_LEVEL = 6

    fun difficultyMixFor(levelIndex: Int): Map<Difficulty, Int> = when (levelIndex) {
        in 1..3 -> mapOf(Difficulty.EASY to WORDS_PER_LEVEL)
        in 4..5 -> DifficultyMix.evenSplit(Difficulty.EASY, Difficulty.MEDIUM, WORDS_PER_LEVEL)
        in 6..7 -> mapOf(Difficulty.MEDIUM to WORDS_PER_LEVEL)
        in 8..9 -> DifficultyMix.evenSplit(Difficulty.MEDIUM, Difficulty.HARD, WORDS_PER_LEVEL)
        else -> mapOf(Difficulty.HARD to WORDS_PER_LEVEL) // 10
    }

    fun levelConfig(worldId: Int, levelIndex: Int): LevelConfig {
        val world = requireNotNull(World.forId(worldId)) { "Bilinmeyen worldId: $worldId" }
        return LevelConfig(world.category, difficultyMixFor(levelIndex))
    }

    /** [completedCounts]: worldId -> that world's number of levels completed at least once. */
    fun isWorldUnlocked(worldId: Int, completedCounts: Map<Int, Int>): Boolean =
        worldId == 1 || (completedCounts[worldId - 1] ?: 0) >= LEVELS_PER_WORLD
}
