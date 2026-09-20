package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.Serializable

/**
 * A kasa (chest) tier — the reward table itself, kept together rather than
 * scattered across GameConstants because unlike GameConstants' game-balance
 * numbers, these three together ARE the whole chest economy.
 */
@Serializable
enum class ChestTier(val unlockDurationMillis: Long, val goldReward: IntRange) {
    SILVER(3 * 60 * 60 * 1000L, 20..40),
    GOLD(8 * 60 * 60 * 1000L, 60..120),
    RARE(24 * 60 * 60 * 1000L, 200..400)
}

/**
 * One slot's contents — up to [ChestSlots.SLOT_COUNT] of these live in
 * SettingsRepository.chestSlots at once. [unlockStartedAtMillis] null means
 * still locked (tap to start counting down); non-null means counting
 * toward [ChestTier.unlockDurationMillis] past that moment.
 */
@Serializable
data class Chest(
    val id: String,
    val tier: ChestTier,
    val unlockStartedAtMillis: Long? = null
) {
    fun isReady(nowMillis: Long): Boolean =
        unlockStartedAtMillis != null && nowMillis >= unlockStartedAtMillis + tier.unlockDurationMillis
}

/** What opening a ready chest actually paid out. */
data class ChestReward(val tier: ChestTier, val gold: Int)

object ChestSlots {
    const val SLOT_COUNT = 4

    // A 240-long cycle, 75% / 20% / 5% — a per-account SHUFFLE of a fixed
    // multiset rather than a fresh weighted roll every time. A genuinely
    // random roll can hand out zero rares in a hundred chests and feel
    // broken even though it technically isn't; a shuffled fixed cycle
    // guarantees the long-run rate while still hiding exactly when the next
    // rare falls. See SettingsRepository.chestCycleSeed/chestCycleIndex for
    // how [seed] and [cycleIndex] are chosen and advanced.
    const val CYCLE_LENGTH = 240
    private const val SILVER_COUNT = 180
    private const val GOLD_COUNT = 48
    private const val RARE_COUNT = 12

    fun tierAt(seed: Long, cycleIndex: Int): ChestTier {
        val bag = buildList {
            repeat(SILVER_COUNT) { add(ChestTier.SILVER) }
            repeat(GOLD_COUNT) { add(ChestTier.GOLD) }
            repeat(RARE_COUNT) { add(ChestTier.RARE) }
        }.shuffled(kotlin.random.Random(seed))
        return bag[((cycleIndex % CYCLE_LENGTH) + CYCLE_LENGTH) % CYCLE_LENGTH]
    }
}
