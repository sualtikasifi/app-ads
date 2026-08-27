package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

class GetWordsForGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend fun getCategories(): List<String> = repository.getCategories()

    /** Whole approved pool — the daily challenge picks from it deterministically. */
    suspend fun getAllApprovedWords(): List<Word> = repository.getAllApprovedWords()

    suspend operator fun invoke(count: Int, category: String?, difficulty: Difficulty? = null): List<Word> =
        repository.getRandomWords(count, category, difficulty)

    /** Level map entry point — [difficultyMix] carries one or two difficulties, each with its own exact word count. */
    suspend operator fun invoke(category: String?, difficultyMix: Map<Difficulty, Int>): List<Word> =
        repository.getRandomWordsMix(category, difficultyMix)
}
