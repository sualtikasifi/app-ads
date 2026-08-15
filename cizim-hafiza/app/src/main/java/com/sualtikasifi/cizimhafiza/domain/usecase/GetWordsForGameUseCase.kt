package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

class GetWordsForGameUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend fun getCategories(): List<String> = repository.getCategories()

    suspend fun countWords(category: String?): Int = repository.countWords(category)

    suspend operator fun invoke(count: Int, category: String?): List<Word> =
        repository.getRandomWords(count, category)
}
