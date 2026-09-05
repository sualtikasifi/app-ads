package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/** A single level's word-selection parameters — fed straight into GetWordsForGameUseCase. */
data class LevelConfig(val difficultyMix: Map<Difficulty, Int>) {
    val wordCount: Int get() = difficultyMix.values.sum()
}

/**
 * Pure level/world math for the "Bölümler" level map — no hand-authored
 * per-level data, and (unlike the category-per-world design this replaced)
 * no category at all: every level draws from the entire word pool mixed
 * together (see GameViewModel.loadWords passing a null category through to
 * GetWordsForGameUseCase, the same "Tümü" path free play uses), so the map
 * is homogeneous in subject matter from World 1 to World 9. What escalates
 * instead, smoothly across all 90 levels rather than resetting every 10, is
 * difficulty.
 *
 * Within any one world, [difficultyMixFor] still ramps EASY→HARD across its
 * 10 levels exactly as it always did: three pure-EASY steps, two
 * EASY/MEDIUM-mixed steps, two pure-MEDIUM steps, two MEDIUM/HARD-mixed
 * steps, and a final pure-HARD step. [worldShift] additionally advances
 * that same 1-10 index by one full step per world beyond the first, so
 * world 2's level 1 starts where world 1's level 2 left off, and so on —
 * by World 9 even its earliest levels are already deep in the MEDIUM/HARD
 * mix. The result is one continuous difficulty curve spanning the whole
 * catalog instead of nine separate easy-to-hard resets.
 */
object LevelCatalog {
    const val LEVELS_PER_WORLD = 10

    // Below the smallest single-difficulty word pool, so no level is ever
    // short on words regardless of which difficulty(-mix) it uses.
    const val WORDS_PER_LEVEL = 6

    private fun difficultyMixForIndex(index: Int): Map<Difficulty, Int> = when (index) {
        in Int.MIN_VALUE..3 -> mapOf(Difficulty.EASY to WORDS_PER_LEVEL)
        in 4..5 -> DifficultyMix.evenSplit(Difficulty.EASY, Difficulty.MEDIUM, WORDS_PER_LEVEL)
        in 6..7 -> mapOf(Difficulty.MEDIUM to WORDS_PER_LEVEL)
        in 8..9 -> DifficultyMix.evenSplit(Difficulty.MEDIUM, Difficulty.HARD, WORDS_PER_LEVEL)
        else -> mapOf(Difficulty.HARD to WORDS_PER_LEVEL) // 10 and beyond
    }

    /** [levelIndex] (1-10) shifted forward by one step per world past the first, capped so it never exceeds the pure-HARD tier. */
    private fun worldShiftedIndex(worldId: Int, levelIndex: Int): Int =
        (levelIndex + (worldId - 1)).coerceAtMost(LEVELS_PER_WORLD)

    fun difficultyMixFor(worldId: Int, levelIndex: Int): Map<Difficulty, Int> =
        difficultyMixForIndex(worldShiftedIndex(worldId, levelIndex))

    fun levelConfig(worldId: Int, levelIndex: Int): LevelConfig =
        LevelConfig(difficultyMixFor(worldId, levelIndex))

    /**
     * The difficulty a level's node badge names. A mixed level reports the
     * harder of its two halves — that half is what decides how the level
     * actually feels, and "Orta/Zor" on a node badge is more noise than
     * information.
     */
    fun headlineDifficulty(worldId: Int, levelIndex: Int): Difficulty =
        difficultyMixFor(worldId, levelIndex).keys.maxByOrNull { it.ordinal } ?: Difficulty.EASY

    /**
     * Ten stage names, reused in every world rather than authored 90 times.
     * The world already supplies the identity ("İlk Çizgiler", "Deha
     * Rotası"); these name the *shape of the climb* inside it, which is the
     * same shape everywhere — a warm-up, a middle where the mix turns, a
     * final test. So a level reads as "Karalama Bahçesi · 4. Karışık Sular"
     * instead of a bare, unlabelled circle.
     */
    @StringRes
    fun levelNameRes(levelIndex: Int): Int = LEVEL_NAME_RES[
        (levelIndex - 1).coerceIn(0, LEVEL_NAME_RES.lastIndex)
    ]

    /**
     * A mark for each stage, matching the name beneath it.
     *
     * The nodes used to carry the level number, which every level map in
     * every game already carries — and which said nothing the position on
     * the path did not. An emblem tied to the stage's own name (a match for
     * the warm-up, a spark, a wave for the flow) makes the ten steps of the
     * climb tell them apart at a glance, and the number is still one line
     * below in the plate's own label.
     */
    fun levelEmblem(levelIndex: Int): String = LEVEL_EMBLEMS[
        (levelIndex - 1).coerceIn(0, LEVEL_EMBLEMS.lastIndex)
    ]

    private val LEVEL_EMBLEMS = arrayOf(
        "🔥", // Isınma / Warm-up
        "✨", // İlk Kıvılcım / First Spark
        "🌊", // Akış / Flow
        "🌀", // Karışık Sular / Mixed Waters
        "⚖️", // Denge / Balance
        "🎯", // Odak / Focus
        "✒️", // Keskin Çizgi / Sharp Line
        "📜", // Sınav / The Test
        "🏔️", // Zirve Tırmanışı / Summit Climb
        "👑"  // Usta Sınavı / Master's Trial
    )

    private val LEVEL_NAME_RES = intArrayOf(
        R.string.level_stage_1,
        R.string.level_stage_2,
        R.string.level_stage_3,
        R.string.level_stage_4,
        R.string.level_stage_5,
        R.string.level_stage_6,
        R.string.level_stage_7,
        R.string.level_stage_8,
        R.string.level_stage_9,
        R.string.level_stage_10
    )

    /** [completedCounts]: worldId -> that world's number of levels completed at least once. */
    fun isWorldUnlocked(worldId: Int, completedCounts: Map<Int, Int>): Boolean =
        worldId == 1 || (completedCounts[worldId - 1] ?: 0) >= LEVELS_PER_WORLD
}
