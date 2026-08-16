package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

/** Records a finished online match into "Son Oyunlar" with the opponent's name and score. */
class SaveOnlineGameSessionUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(
        totalScore: Int,
        wordCount: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        opponentName: String,
        opponentScore: Int
    ) = repository.saveOnlineGameSession(totalScore, wordCount, correctCount, fastestCorrectMs, opponentName, opponentScore)
}
