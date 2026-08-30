package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.XpAwards
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import javax.inject.Inject

data class GuessOutcome(val isCorrect: Boolean, val pointsAwarded: Int, val xpAwarded: Int)

/** Scores one typed guess: Turkish-tolerant match + optional speed bonus, for both points and XP. */
class SubmitGuessUseCase @Inject constructor() {

    operator fun invoke(userAnswer: String, target: String, responseTimeMs: Long, difficulty: Difficulty): GuessOutcome {
        // Tolerance is derived from the target's own length rather than a
        // flat constant — see AnswerMatcher.toleranceFor for why a fixed 2
        // made unrelated short words score against each other.
        val correct = AnswerMatcher.isCorrect(userAnswer = userAnswer, target = target)
        if (!correct) return GuessOutcome(isCorrect = false, pointsAwarded = GameConstants.POINTS_WRONG, xpAwarded = 0)

        var points = GameConstants.POINTS_CORRECT
        if (GameConstants.SPEED_BONUS_ENABLED && responseTimeMs < GameConstants.SPEED_BONUS_THRESHOLD_MS) {
            points += GameConstants.SPEED_BONUS_POINTS
        }
        val xp = XpAwards.wordXp(difficulty = difficulty, responseTimeMs = responseTimeMs)
        return GuessOutcome(isCorrect = true, pointsAwarded = points, xpAwarded = xp)
    }
}
