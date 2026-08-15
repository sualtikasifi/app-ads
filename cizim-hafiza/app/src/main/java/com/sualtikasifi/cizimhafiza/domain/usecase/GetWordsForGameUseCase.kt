package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

class GetWordsForGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend fun getCategories(): List<String> = repository.getCategories()

    suspend fun countWords(category: String?, difficulty: Difficulty? = null): Int =
        repository.countWords(category, difficulty)

    suspend operator fun invoke(count: Int, category: String?, difficulty: Difficulty? = null): List<Word> =
        repository.getRandomWords(count, category, difficulty)
}
