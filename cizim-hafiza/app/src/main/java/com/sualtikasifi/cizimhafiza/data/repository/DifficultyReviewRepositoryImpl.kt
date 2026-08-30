package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.DifficultyReviewDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DifficultyReviewEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DifficultyReviewCounts
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.DifficultyReviewRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class ExportedDifficultyWord(val id: Int, val text: String, val category: String, val difficulty: String)

@Serializable
private data class DifficultyReviewExport(
    val exportedAtMillis: Long,
    val classified: List<ExportedDifficultyWord>
)

class DifficultyReviewRepositoryImpl @Inject constructor(
    private val reviewDao: DifficultyReviewDao,
    private val wordDao: WordDao
) : DifficultyReviewRepository {

    private val exportJson = Json { prettyPrint = true }

    override suspend fun getNextPendingWord(): Word? = reviewDao.getNextPendingWord()?.toDomain()

    override suspend fun getCounts(): DifficultyReviewCounts = DifficultyReviewCounts(
        pending = reviewDao.getPendingCount(),
        easy = reviewDao.getEasyCount(),
        medium = reviewDao.getMediumCount(),
        hard = reviewDao.getHardCount()
    )

    override suspend fun setDifficulty(wordId: Int, difficulty: Difficulty) {
        // difficulty lets the reviewer's own device immediately use their new
        // classification (e.g. in World Map levels) — it does NOT reach any
        // other player until the export is folded into words.json.
        wordDao.updateDifficulty(wordId, difficulty)
        reviewDao.upsert(
            DifficultyReviewEntity(wordId = wordId, difficulty = difficulty, reviewedAtMillis = System.currentTimeMillis())
        )
    }

    override suspend fun exportReviewedDifficultiesJson(): String {
        val rows = reviewDao.getAllReviewed()
        val export = DifficultyReviewExport(
            exportedAtMillis = System.currentTimeMillis(),
            classified = rows.map { ExportedDifficultyWord(it.id, it.text, it.category, it.difficulty) }
        )
        return exportJson.encodeToString(DifficultyReviewExport.serializer(), export)
    }
}
