package com.sualtikasifi.cizimhafiza.presentation.game

import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.Word
import kotlinx.serialization.Serializable

sealed interface GamePhase {

    data object Loading : GamePhase

    data class Drawing(
        val word: Word,
        val wordNumber: Int,
        val totalWords: Int,
        val secondsLeft: Int,
        val totalSeconds: Int,
        val isWarning: Boolean,
        val strokes: List<DrawingStroke>,
        /** RELAXED mode: no countdown — the drawer taps "next word" manually. */
        val isUntimed: Boolean = false,
        /** Estimated seconds until the whole match (all remaining words'
         * draw+break+guess phases) finishes. Null in RELAXED/[isUntimed]
         * mode, where per-word duration isn't fixed so no estimate exists. */
        val matchSecondsRemaining: Int? = null,
        /** This match's one-per-match rewarded-ad "+time" hint — separate
         * budget from [Guessing.hintUsed], so a player can use one of each
         * per match. Never true in [isUntimed] mode (nothing to extend). */
        val hintUsed: Boolean = false
    ) : GamePhase

    data class Break(val secondsLeft: Int, val totalSeconds: Int) : GamePhase

    data class Guessing(
        val guessNumber: Int,
        val totalGuesses: Int,
        val strokes: List<DrawingStroke>,
        val feedback: GuessFeedback?,
        val secondsLeft: Int,
        val totalSeconds: Int,
        val isWarning: Boolean,
        /** Whether this match's one-per-match rewarded-ad hint has already
         * been spent (on any word, not just this one) — once true, stays
         * true for the rest of the match. */
        val hintUsed: Boolean = false,
        /** The revealed first letter, only for THIS word — reset to null
         * every time a new word's guess turn starts, even if [hintUsed]
         * stays true. */
        val hintLetter: String? = null
    ) : GamePhase

    // @Serializable so a finished match can be checkpointed into
    // SavedStateHandle and restored verbatim after process death (see
    // GameViewModel's recovery snapshot) — the match outcome (score, XP,
    // streak) was already persisted by finishGame() before this phase was
    // ever set, so restoring it is just redisplaying the same result, never
    // recomputing or re-awarding it.
    @Serializable
    data class Result(
        val totalScore: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val fastestCorrectSeconds: Double?,
        val items: List<ResultItem>,
        // Non-null only for a level-map play (see LevelCatalog) — null for free play.
        val levelStars: Int? = null,
        // Non-null only for a daily challenge run — carries what the result
        // screen needs to show the streak, the XP earned and Sude's score.
        val daily: DailyResultSummary? = null,
        // Non-null only when this round was played as a duel challenge (see
        // C2 / GameViewModel's duel args) — the friend it was just sent to,
        // so the result screen can say so instead of just showing a normal
        // solo score.
        val duelOpponentName: String? = null
    ) : GamePhase
}

/** The daily-challenge-only half of a [GamePhase.Result]. */
@Serializable
data class DailyResultSummary(
    val streak: Int,
    val xpEarned: Int,
    /** True when finishing today's challenge raised the streak multiplier — see XpAwards.dailyStreakMultiplierJustIncreased. */
    val streakMultiplierIncreased: Boolean,
    /** The streak multiplier this payout used (see XpAwards.dailyStreakMultiplier). */
    val streakMultiplier: Int
)

data class GuessFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    /** 0 when wrong, or when this run doesn't award live per-word XP (the daily challenge — see XpAwards.wordXp). */
    val xpAwarded: Int = 0
)
