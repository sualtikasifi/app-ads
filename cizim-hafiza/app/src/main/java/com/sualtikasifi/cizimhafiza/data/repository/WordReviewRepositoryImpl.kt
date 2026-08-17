package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.ReviewedWordRow
import com.sualtikasifi.cizimhafiza.data.local.dao.WordReviewDao
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.WordReviewStatus
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.WordReviewCounts
import com.sualtikasifi.cizimhafiza.domain.repository.WordReviewRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class ExportedWord(val id: Int, val text: String, val category: String, val difficulty: String)

@Serializable
private data class ReviewExport(
    val exportedAtMillis: Long,
    val kept: List<ExportedWord>,
    val deleted: List<ExportedWord>
)

class WordReviewRepositoryImpl @Inject constructor(
    private val dao: WordReviewDao
) : WordReviewRepository {

    private val exportJson = Json { prettyPrint = true }

    override suspend fun getNextPendingWord(): Word? =
        dao.getNextPendingWord(GameConstants.LEGACY_WORD_ID_MAX)?.toDomain()

    override suspend fun getCounts(): WordReviewCounts = WordReviewCounts(
        pending = dao.getPendingCount(GameConstants.LEGACY_WORD_ID_MAX),
        kept = dao.getKeptCount(),
        deleted = dao.getDeletedCount()
    )

    override suspend fun keep(wordId: Int) = review(wordId, WordReviewStatus.KEPT)
    override suspend fun delete(wordId: Int) = review(wordId, WordReviewStatus.DELETED)

    override suspend fun exportReviewedWordsJson(): String {
        val rows = dao.getAllReviewed()
        val export = ReviewExport(
            exportedAtMillis = System.currentTimeMillis(),
            kept = rows.filter { it.status == WordReviewStatus.KEPT }.map { it.toExported() },
            deleted = rows.filter { it.status == WordReviewStatus.DELETED }.map { it.toExported() }
        )
        return exportJson.encodeToString(ReviewExport.serializer(), export)
    }

    private fun ReviewedWordRow.toExported() = ExportedWord(id, text, category, difficulty)

    private suspend fun review(wordId: Int, status: String) {
        dao.upsert(WordReviewEntity(wordId = wordId, status = status, reviewedAtMillis = System.currentTimeMillis()))
    }
}
