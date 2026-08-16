package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.WordReviewDao
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewStatus
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.WordReviewCounts
import com.sualtikasifi.cizimhafiza.domain.repository.WordReviewRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import javax.inject.Inject

class WordReviewRepositoryImpl @Inject constructor(
    private val dao: WordReviewDao
) : WordReviewRepository {

    override suspend fun getNextPendingWord(): Word? =
        dao.getNextPendingWord(GameConstants.LEGACY_WORD_ID_MAX)?.toDomain()

    override suspend fun getCounts(): WordReviewCounts = WordReviewCounts(
        pending = dao.getPendingCount(GameConstants.LEGACY_WORD_ID_MAX),
        kept = dao.getKeptCount(),
        deleted = dao.getDeletedCount()
    )

    override suspend fun keep(wordId: Int) = review(wordId, WordReviewStatus.KEPT)
    override suspend fun delete(wordId: Int) = review(wordId, WordReviewStatus.DELETED)

    private suspend fun review(wordId: Int, status: String) {
        dao.upsert(WordReviewEntity(wordId = wordId, status = status, reviewedAtMillis = System.currentTimeMillis()))
    }
}
