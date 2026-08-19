package com.sualtikasifi.cizimhafiza.presentation.game

import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.Word

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
        val matchSecondsRemaining: Int? = null
    ) : GamePhase

    data class Break(val secondsLeft: Int, val totalSeconds: Int) : GamePhase

    data class Guessing(
        val guessNumber: Int,
        val totalGuesses: Int,
        val strokes: List<DrawingStroke>,
        val feedback: GuessFeedback?,
        val secondsLeft: Int,
        val totalSeconds: Int,
        val isWarning: Boolean
    ) : GamePhase

    data class Result(
        val totalScore: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val fastestCorrectSeconds: Double?,
        val items: List<ResultItem>,
        // Non-null only for a level-map play (see LevelCatalog) — null for free play.
        val levelStars: Int? = null
    ) : GamePhase
}

data class GuessFeedback(val isCorrect: Boolean, val correctAnswer: String)
