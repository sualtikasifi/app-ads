package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Achievement
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

class SaveGameSessionUseCase @Inject constructor(
    private val repository: GameRepository
) {
    /** Returns any achievements newly unlocked by this save. */
    suspend operator fun invoke(results: List<DrawingResult>): List<Achievement> {
        val totalScore = results.sumOf { it.pointsAwarded }
        return repository.saveGame(totalScore, results)
    }
}
