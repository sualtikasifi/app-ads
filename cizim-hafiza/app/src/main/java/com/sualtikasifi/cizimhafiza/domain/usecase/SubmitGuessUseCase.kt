package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import javax.inject.Inject

data class GuessOutcome(val isCorrect: Boolean, val pointsAwarded: Int)

/** Scores one typed guess: Turkish-tolerant match + optional speed bonus. */
class SubmitGuessUseCase @Inject constructor() {

    operator fun invoke(userAnswer: String, target: String, responseTimeMs: Long): GuessOutcome {
        val correct = AnswerMatcher.isCorrect(
            userAnswer = userAnswer,
            target = target,
            tolerance = GameConstants.ANSWER_LEVENSHTEIN_TOLERANCE
        )
        if (!correct) return GuessOutcome(isCorrect = false, pointsAwarded = GameConstants.POINTS_WRONG)

        var points = GameConstants.POINTS_CORRECT
        if (GameConstants.SPEED_BONUS_ENABLED && responseTimeMs < GameConstants.SPEED_BONUS_THRESHOLD_MS) {
            points += GameConstants.SPEED_BONUS_POINTS
        }
        return GuessOutcome(isCorrect = true, pointsAwarded = points)
    }
}
