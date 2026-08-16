package com.sualtikasifi.cizimhafiza.domain.usecase

import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import javax.inject.Inject

/** Online matches: both players resolve the same shared word-id list to identical [Word]s. */
class GetWordsByIdsUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(ids: List<Int>): List<Word> = repository.getWordsByIds(ids)
}
