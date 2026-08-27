package com.sualtikasifi.cizimhafiza.domain.model

import kotlin.random.Random

/**
 * The same short round of words for everybody, every day.
 *
 * Two properties make the whole feature work and neither is negotiable:
 *
 *  - **Everyone gets the same words.** Without that there is nothing to
 *    compare, nothing to talk about and nothing worth sharing — the entire
 *    reason a daily challenge beats "just play a game" evaporates.
 *  - **It is derived, not fetched.** The word ids come out of the date, so
 *    there is no backend to run, no config to ship and no way for the
 *    challenge to be unavailable when someone opens the app offline.
 *
 * The difficulty mix is fixed rather than random so scores stay comparable
 * from one day to the next — a day that happened to roll five HARD words
 * would make everyone's streak look like a slump.
 *
 * **Known limitation:** selection indexes into this device's approved word
 * pool, so two devices only agree while their pools agree. WordPoolSynchronizer
 * re-syncs on every app update, so players on the same app version match;
 * someone running a much older version may see a different set.
 */
object DailyChallenge {

    const val WORD_COUNT = 5

    /** How many of [WORD_COUNT] come from each difficulty, easiest first. */
    private val DIFFICULTY_MIX = listOf(
        Difficulty.EASY to 2,
        Difficulty.MEDIUM to 2,
        Difficulty.HARD to 1
    )

    /**
     * Today's words, in the order they should be played.
     *
     * [pool] is every approved word; it gets sorted by id here so the caller's
     * ordering (SQL collation, locale) can't change what the seed picks.
     * [languageTag] separates the TR and EN pools, which hold different words
     * under overlapping ids.
     */
    fun wordsFor(epochDay: Long, languageTag: String, pool: List<Word>): List<Word> {
        if (pool.isEmpty()) return emptyList()
        val byDifficulty = pool.sortedBy { it.id }.groupBy { it.difficulty }

        val picked = mutableListOf<Word>()
        val used = mutableSetOf<Int>()
        DIFFICULTY_MIX.forEach { (difficulty, perDay) ->
            // Each difficulty is dealt from its own shuffled deck rather than
            // drawn at random every day. Independent daily draws would let
            // yesterday's word come up again today, which reads as a bug —
            // and with only ~83 HARD words that would happen often. Walking a
            // deck instead guarantees no repeat until the whole difficulty
            // has been used, then reshuffles into a different order.
            picked += dealFromDeck(
                deck = byDifficulty[difficulty].orEmpty(),
                perDay = perDay,
                epochDay = epochDay,
                streamSeed = seedFor(0, languageTag) + difficulty.ordinal
            )
            used += picked.map { it.id }
        }

        // A pool too thin in some difficulty still owes the player a full
        // round — top up from whatever is left rather than serving a short one.
        if (picked.size < WORD_COUNT) {
            val random = Random(seedFor(epochDay, languageTag))
            val leftovers = pool.sortedBy { it.id }.filter { it.id !in used }.toMutableList()
            while (picked.size < WORD_COUNT && leftovers.isNotEmpty()) {
                picked += leftovers.removeAt(random.nextInt(leftovers.size))
            }
        }
        return picked
    }

    /**
     * Takes [perDay] words from a deterministically shuffled [deck], at the
     * slot [epochDay] maps to. The deck is reshuffled with a different seed
     * on each full pass, so a long-running player doesn't see the same
     * sequence loop back around in the same order.
     */
    private fun dealFromDeck(deck: List<Word>, perDay: Int, epochDay: Long, streamSeed: Long): List<Word> {
        if (deck.isEmpty() || perDay <= 0) return emptyList()
        val daysPerCycle = deck.size / perDay
        if (daysPerCycle <= 0) return deck.take(perDay)
        // floorDiv/mod, not / and %, so the arithmetic stays correct for the
        // pre-1970 epochDay values a device with a badly wrong clock reports.
        val cycleIndex = Math.floorDiv(epochDay, daysPerCycle.toLong())
        val dayInCycle = Math.floorMod(epochDay, daysPerCycle.toLong()).toInt()
        val shuffled = deck.shuffled(Random(streamSeed + cycleIndex * CYCLE_SEED_STRIDE))
        return shuffled.subList(dayInCycle * perDay, dayInCycle * perDay + perDay)
    }

    /**
     * Sude's score for the same day — deterministic, so every player sees her
     * having done the identical thing, and beatable often enough to be worth
     * racing. Weighted toward 3-4 out of 5: an opponent who always aces it
     * stops being a target and becomes a wall.
     */
    fun botCorrectCount(epochDay: Long, languageTag: String): Int {
        val outcomes = intArrayOf(2, 3, 3, 3, 4, 4, 4, 5)
        return outcomes[Random(seedFor(epochDay, languageTag) + BOT_SEED_OFFSET).nextInt(outcomes.size)]
    }

    private fun seedFor(epochDay: Long, languageTag: String): Long =
        epochDay * 31L + languageTag.hashCode()

    private const val BOT_SEED_OFFSET = 7919L

    /** Keeps consecutive deck reshuffles from landing on neighbouring seeds. */
    private const val CYCLE_SEED_STRIDE = 104_729L
}

/**
 * One finished attempt, kept so the result screen and the share card can be
 * rebuilt without replaying the round.
 */
data class DailyChallengeResult(
    val epochDay: Long,
    /** One flag per word, in play order — drives the share card's ✅/❌ row. */
    val correctFlags: List<Boolean>,
    val score: Int,
    val streakAfter: Int,
    val xpEarned: Int
) {
    val correctCount: Int get() = correctFlags.count { it }
}
