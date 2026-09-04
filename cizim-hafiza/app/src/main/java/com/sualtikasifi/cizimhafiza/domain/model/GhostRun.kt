package com.sualtikasifi.cizimhafiza.domain.model

/**
 * One finished round, recorded so somebody else can play against it later.
 *
 * A Karalak match is not a live exchange — both players draw the same words,
 * recall them, and only the scores are ever compared. Nobody watches anybody
 * draw. So an opponent does not have to be present: a recorded round carries
 * everything a match needs, and "Hızlı Eşleş" hands you one instead of making
 * you wait for a stranger to be online at the same second.
 *
 * The consequence worth understanding: the opponent pool grows with games
 * PLAYED, not with players installed. One person playing twenty rounds a day
 * leaves twenty opponents behind them.
 */
data class GhostRunWord(
    val wordId: Int,
    val isCorrect: Boolean,
    val responseTimeMs: Long,
    val pointsAwarded: Int
)

object GhostRuns {

    /**
     * Levels are matched in bands of ten rather than as a range.
     *
     * Firestore permits an inequality on only ONE field per query, and that
     * one is spent on [SHARD_COUNT] below — without it every player on earth
     * would be handed the same lowest-sorted opponent. So the level filter
     * has to be an equality, which means bucketing it first.
     */
    const val BAND_SIZE = 10

    /**
     * Every run is stamped with a random 0..999 shard at write time, and the
     * matching query asks for `shard >= <random pivot>`. This is the standard
     * way to pull an arbitrary document out of Firestore, which has no
     * "order by random".
     */
    const val SHARD_COUNT = 1000

    /**
     * A round has to be worth playing against. Someone who quit after one
     * word, or scored nothing, makes a dispiriting opponent and clogs the
     * pool with rounds nobody wants to be matched against.
     */
    const val MIN_CORRECT = 2

    /**
     * Runs kept per player; the oldest are dropped past this. Nobody needs a
     * stranger's four-hundredth round, and this is what stops storage from
     * growing without limit as the same people keep playing.
     */
    const val MAX_RUNS_PER_PLAYER = 10

    fun levelBandFor(level: Int): Int = (level.coerceAtLeast(1) - 1) / BAND_SIZE

    /**
     * Whether this finished round should be left behind as an opponent.
     *
     * Four rounds are deliberately never recorded:
     *  - **RELAXED**, which has no countdown at all, so its score cannot be
     *    compared with a timed one.
     *  - **Level rounds**, whose word set belongs to the level rather than
     *    being freely drawn — matching against one would hand the challenger
     *    a level's words out of context.
     *  - **The daily challenge**, because everyone plays the same words that
     *    day: being matched against one would replay the exact round the
     *    player just finished.
     *  - **Anything under [MIN_CORRECT]**, per the pool-quality rule above.
     */
    fun isWorthRecording(
        mode: GameMode,
        isLevelRound: Boolean,
        isDailyChallenge: Boolean,
        correctCount: Int
    ): Boolean =
        mode == GameMode.NORMAL &&
            !isLevelRound &&
            !isDailyChallenge &&
            correctCount >= MIN_CORRECT
}
