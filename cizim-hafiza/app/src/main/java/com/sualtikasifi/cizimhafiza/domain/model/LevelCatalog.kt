package com.sualtikasifi.cizimhafiza.domain.model

/** A single level's word-selection parameters — fed straight into GetWordsForGameUseCase. */
data class LevelConfig(val category: String, val difficulty: Difficulty, val wordCount: Int)

/**
 * Pure level/world math for the "Bölümler" level map — no hand-authored
 * per-level data. A level's content is entirely derived from its world's
 * category and its position (1-9) within that world.
 */
object LevelCatalog {
    const val LEVELS_PER_WORLD = 9

    // Below the smallest single-difficulty word pool (Spor/EASY = 7), so no
    // level is ever short on words regardless of which world/difficulty it uses.
    const val WORDS_PER_LEVEL = 6

    fun difficultyFor(levelIndex: Int): Difficulty = when (levelIndex) {
        in 1..3 -> Difficulty.EASY
        in 4..6 -> Difficulty.MEDIUM
        else -> Difficulty.HARD // 7..9
    }

    fun levelConfig(worldId: Int, levelIndex: Int): LevelConfig {
        val world = requireNotNull(World.forId(worldId)) { "Bilinmeyen worldId: $worldId" }
        return LevelConfig(world.category, difficultyFor(levelIndex), WORDS_PER_LEVEL)
    }

    /** [completedCounts]: worldId -> that world's number of levels completed at least once. */
    fun isWorldUnlocked(worldId: Int, completedCounts: Map<Int, Int>): Boolean =
        worldId == 1 || (completedCounts[worldId - 1] ?: 0) >= LEVELS_PER_WORLD
}
