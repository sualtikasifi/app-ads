package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GameRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val gameSessionDao: GameSessionDao,
    private val drawingResultDao: DrawingResultDao
) : GameRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): List<String> = wordDao.getCategories()

    override suspend fun countWords(category: String?): Int = wordDao.countByCategory(category)

    override suspend fun getRandomWords(count: Int, category: String?): List<Word> =
        wordDao.getRandomWords(count, category).map(WordEntity::toDomain)

    override suspend fun saveGame(totalScore: Int, results: List<DrawingResult>): Long {
        val fastest = results.filter { it.isCorrect }.minOfOrNull { it.responseTimeMs }
        val sessionId = gameSessionDao.insert(
            GameSessionEntity(
                date = System.currentTimeMillis(),
                totalScore = totalScore,
                wordCount = results.size,
                correctCount = results.count { it.isCorrect },
                fastestCorrectMs = fastest
            )
        )

        val entities = results.map { result ->
            DrawingResultEntity(
                sessionId = sessionId,
                wordId = result.wordId,
                pathDataJson = json.encodeToString(result.strokes),
                isCorrect = result.isCorrect,
                userAnswer = result.userAnswer,
                responseTimeMs = result.responseTimeMs,
                pointsAwarded = result.pointsAwarded
            )
        }
        drawingResultDao.insertAll(entities)
        return sessionId
    }

    override fun observeStatistics(): Flow<GameStatistics> =
        gameSessionDao.observeSessions().map { sessions ->
            GameStatistics(
                sessions = sessions.map { it.toDomain() },
                bestScore = sessions.maxOfOrNull { it.totalScore } ?: 0,
                totalWordsPlayed = sessions.sumOf { it.wordCount }
            )
        }
}
