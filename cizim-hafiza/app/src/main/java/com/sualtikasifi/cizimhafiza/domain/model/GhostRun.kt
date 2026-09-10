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
     * Every recorded round is exactly this many words, whatever length or
     * kind of round it actually was.
     *
     * A SINGLE fixed length, because a match can only be set against a round
     * of the same length: recording several would split the pool that many
     * ways and make an opponent that much harder to find — with a handful of
     * players that is the difference between a match and an empty screen. A
     * longer round leaves behind only its first [RUN_WORD_COUNT] words;
     * scoring is per-word and independent, so those stand on their own
     * exactly as a same-length round would.
     *
     * Ten, matching free play's shortest round — a quick match is the mode
     * meant to feel like a real bout, and six words was over before it
     * started. The cost is that the level campaign can no longer feed the
     * pool at all: a level is [LevelCatalog.WORDS_PER_LEVEL] words, fewer
     * than this floor, so [recordableSlice] turns every one of them away.
     * That is survivable now only because the pool has a second source —
     * quick matches themselves are ten words and DO record, as do free play
     * and online rounds, and an empty pool falls back to a synthesized
     * opponent (see BotGhostRuns) rather than an empty screen.
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
     * Two are deliberately never recorded:
     *  - **RELAXED**, which has no countdown at all, so its score cannot be
     *    compared with a timed one.
     *  - **The daily challenge**, because everyone plays the same words that
     *    day: being matched against one would replay the exact round the
     *    player just finished.
     *
     * Level rounds are not excluded HERE — there is nothing level-specific
     * about their words, which are drawn from the same pool free play draws
     * from (see [LevelCatalog]). They simply cannot pass [recordableSlice]:
     * a level is [LevelCatalog.WORDS_PER_LEVEL] words, short of
     * [RUN_WORD_COUNT]. Keeping that as a length rule rather than a second
     * mode check means a level would start contributing on its own the day
     * either number changes to meet the other.
     *
     * Whether the round is GOOD enough is a separate question, also answered
     * by [recordableSlice] — which asks it of the [RUN_WORD_COUNT] words
     * actually recorded rather than of the whole round.
     */
    fun isWorthRecording(
        mode: GameMode,
        isDailyChallenge: Boolean
    ): Boolean = mode == GameMode.NORMAL && !isDailyChallenge

    /**
     * Cuts a finished round down to the [RUN_WORD_COUNT] words that get
     * stored, and recomputes its totals over exactly those words.
     *
     * Returns null when there is nothing worth storing: a round shorter than
     * [RUN_WORD_COUNT] (which free play cannot produce, but a future entry
     * point might), or one whose recorded slice is under [MIN_CORRECT].
     *
     * The totals are recomputed rather than passed in on purpose. A longer
     * round's own score describes all of its words; storing it against just
     * [RUN_WORD_COUNT] would hand every challenger an opponent they could
     * not possibly beat.
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
