package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.DrawingResultEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.GameSessionEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.data.local.entity.WordEntity
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DifficultyMix
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.GameRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GameRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val gameSessionDao: GameSessionDao,
    private val drawingResultDao: DrawingResultDao,
    private val settingsRepository: SettingsRepository
) : GameRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getCategories(): List<String> = wordDao.getCategories()

    override suspend fun getRandomWords(count: Int, category: String?, difficulty: Difficulty?): List<Word> {
        if (difficulty != null) {
            return wordDao.getRandomWords(count, category, difficulty.name).map(WordEntity::toDomain)
        }
        // "Tümü" (no specific difficulty): draw with a fixed EASY/MEDIUM/HARD
        // curve instead of pure uniform random, so a round can't luck into
        // being all-EASY or all-HARD — see DifficultyMix.
        return getRandomWordsMix(category, DifficultyMix.allDifficulties(count))
    }

    // World Map levels within the same world (= category) draw independently
    // of one another with no shared memory, so without this, the same word
    // can easily resurface a level or two later — worst for a world's thin
    // categories (e.g. very few HARD words), where consecutive levels could
    // end up asking almost the exact same small set repeatedly. Recently
    // drawn words in this category (from any past game) are excluded first;
    // see pickAvoidingRecent for the thin-pool fallback.
    override suspend fun getRandomWordsMix(category: String?, mix: Map<Difficulty, Int>): List<Word> {
        val recentIds = category
            ?.let { drawingResultDao.getRecentWordIds(it, GameConstants.RECENT_WORD_EXCLUSION_WINDOW) }
            .orEmpty()
        return mix.flatMap { (difficulty, n) -> pickAvoidingRecent(n, category, difficulty, recentIds) }
            .shuffled()
            .map(WordEntity::toDomain)
    }

    private suspend fun pickAvoidingRecent(
        limit: Int,
        category: String?,
        difficulty: Difficulty,
        recentIds: List<Int>
    ): List<WordEntity> {
        val avoiding = wordDao.getRandomWordsExcluding(limit, category, difficulty.name, recentIds)
        if (avoiding.size >= limit) return avoiding
        // This difficulty's pool is too thin to fill the level's quota
        // while avoiding recent words — top up from them instead of
        // shorting the level, but still never repeat a word within this
        // same level (excludes what avoiding already picked, not recentIds).
        val topUp = wordDao.getRandomWordsExcluding(
            limit - avoiding.size, category, difficulty.name, avoiding.map { it.id }
        )
        return avoiding + topUp
    }

    override suspend fun getWordsByIds(ids: List<Int>): List<Word> {
        val byId = wordDao.getWordsByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

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
        gameSessionDao.pruneOlderThan(GameConstants.RECENT_GAMES_LIMIT)
        settingsRepository.addScore(totalScore)
        settingsRepository.updateStreakOnPlay()
        return sessionId
    }

    override suspend fun saveOnlineGameSession(
        totalScore: Int,
        wordCount: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        placement: Int,
        playerCount: Int
    ) {
        gameSessionDao.insert(
            GameSessionEntity(
                date = System.currentTimeMillis(),
                totalScore = totalScore,
                wordCount = wordCount,
                correctCount = correctCount,
                fastestCorrectMs = fastestCorrectMs,
                placement = placement,
                playerCount = playerCount
            )
        )
        gameSessionDao.pruneOlderThan(GameConstants.RECENT_GAMES_LIMIT)
        settingsRepository.addScore(totalScore)
        settingsRepository.updateStreakOnPlay()
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
