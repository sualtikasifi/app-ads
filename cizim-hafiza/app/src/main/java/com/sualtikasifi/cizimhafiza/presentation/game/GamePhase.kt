package com.sualtikasifi.cizimhafiza.presentation.game

import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
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
        val isUntimed: Boolean = false
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
        val items: List<ResultItem>
    ) : GamePhase
}

data class GuessFeedback(val isCorrect: Boolean, val correctAnswer: String)

data class ResultItem(val word: String, val isCorrect: Boolean, val strokes: List<DrawingStroke>)
