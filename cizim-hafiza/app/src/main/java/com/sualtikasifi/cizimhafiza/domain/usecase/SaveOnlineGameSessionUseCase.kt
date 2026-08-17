package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

/** Records a finished online match into "Son Oyunlar" with this player's placement (1-indexed rank) in the room. */
class SaveOnlineGameSessionUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(
        totalScore: Int,
        wordCount: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        placement: Int,
        playerCount: Int
    ) = repository.saveOnlineGameSession(totalScore, wordCount, correctCount, fastestCorrectMs, placement, playerCount)
}
