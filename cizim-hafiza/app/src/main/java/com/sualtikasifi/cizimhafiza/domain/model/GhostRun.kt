package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.Serializable

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

/**
 * A recorded round as the matching query reads it back — everything needed to
 * offer the match and set up the round, and nothing more.
 *
 * Deliberately WITHOUT the opponent's drawings. Those are the bulk of a
 * recorded round (roughly a hundred kilobytes against this object's one) and
 * they are worth nothing until the challenger has finished playing, so they
 * live in their own document and are fetched at the very end — see
 * GhostRunRepository.loadItems.
 *
 * Serializable because it is handed to the game screen as a single
 * url-encoded navigation argument (see Screen.quickMatchGameRoute) — small
 * enough to travel in a route precisely because the drawings are not in it.
 */
@Serializable
data class GhostRun(
    val id: String,
    val uid: String,
    val nickname: String,
    val level: Int,
    val frameId: String,
    val wordIds: List<Int>,
    val totalScore: Int,
    val correctCount: Int,
    val fastestCorrectMs: Long?
)

/**
 * The slice of a finished round that actually gets written — see
 * [GhostRuns.recordableSlice].
 */
data class RecordableGhostRun(
    val wordIds: List<Int>,
    val perWord: List<GhostRunWord>,
    val items: List<ResultItem>,
    val totalScore: Int,
    val correctCount: Int,
    val fastestCorrectMs: Long?
)

object GhostRuns {

    /**
     * Every recorded round is exactly this many words, whatever length the
     * player actually chose (free play offers 10 to 50).
     *
     * Two problems, one answer. A match can only be set against a round of
     * the same length, so recording five different lengths would split the
     * pool five ways and make an opponent five times harder to find — with a
     * handful of players that is the difference between a match and an empty
     * screen. And a round's drawings run to roughly ten kilobytes a word, so
     * a fifty-word round would be half a megabyte of stroke data for a match
     * nobody would sit through anyway.
     *
     * A longer round therefore leaves behind its first ten words. Scoring is
     * per-word and independent, so those ten stand on their own exactly as a
     * ten-word round would.
     */
    const val RUN_WORD_COUNT = 10

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
     * Whether this finished round is the KIND of round that gets recorded.
     *
     * Three are deliberately never recorded:
     *  - **RELAXED**, which has no countdown at all, so its score cannot be
     *    compared with a timed one.
     *  - **Level rounds**, whose word set belongs to the level rather than
     *    being freely drawn — matching against one would hand the challenger
     *    a level's words out of context.
     *  - **The daily challenge**, because everyone plays the same words that
     *    day: being matched against one would replay the exact round the
     *    player just finished.
     *
     * Whether the round is GOOD enough is a separate question, answered by
     * [recordableSlice] — which asks it of the ten words actually recorded
     * rather than of the whole round.
     */
    fun isWorthRecording(
        mode: GameMode,
        isLevelRound: Boolean,
        isDailyChallenge: Boolean
    ): Boolean = mode == GameMode.NORMAL && !isLevelRound && !isDailyChallenge

    /**
     * Cuts a finished round down to the [RUN_WORD_COUNT] words that get
     * stored, and recomputes its totals over exactly those words.
     *
     * Returns null when there is nothing worth storing: a round shorter than
     * [RUN_WORD_COUNT] (which free play cannot produce, but a future entry
     * point might), or one whose recorded slice is under [MIN_CORRECT].
     *
     * The totals are recomputed rather than passed in on purpose. A fifty-word
     * round's own score describes fifty words; storing it against ten would
     * hand every challenger an opponent they could not possibly beat.
     */
    fun recordableSlice(
        wordIds: List<Int>,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    ): RecordableGhostRun? {
        if (wordIds.size < RUN_WORD_COUNT) return null
        if (perWord.size < RUN_WORD_COUNT || items.size < RUN_WORD_COUNT) return null

        val slicedWords = perWord.take(RUN_WORD_COUNT)
        val correct = slicedWords.count { it.isCorrect }
        if (correct < MIN_CORRECT) return null

        return RecordableGhostRun(
            wordIds = wordIds.take(RUN_WORD_COUNT),
            perWord = slicedWords,
            items = items.take(RUN_WORD_COUNT),
            totalScore = slicedWords.sumOf { it.pointsAwarded },
            correctCount = correct,
            fastestCorrectMs = slicedWords.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        )
    }
}
