package com.sualtikasifi.cizimhafiza.data.repository

import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgress
import com.sualtikasifi.cizimhafiza.domain.model.LevelStars
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LevelProgressRepositoryImpl @Inject constructor(
    private val dao: LevelProgressDao
) : LevelProgressRepository {

    override fun observeAllProgress(): Flow<List<LevelProgress>> =
        dao.observeAllProgress().map { it.map(LevelProgressEntity::toDomain) }

    override fun observeWorldProgress(worldId: Int): Flow<List<LevelProgress>> =
        dao.observeWorldProgress(worldId).map { it.map(LevelProgressEntity::toDomain) }

    override suspend fun recordLevelResult(worldId: Int, levelIndex: Int, correctCount: Int, totalWords: Int, score: Int): Int {
        val stars = LevelStars.forAccuracy(correctCount, totalWords)
        val existing = dao.getOne(worldId, levelIndex)
        dao.upsert(
            LevelProgressEntity(
                worldId = worldId,
                levelIndex = levelIndex,
                bestStars = maxOf(stars, existing?.bestStars ?: 0),
                bestScore = maxOf(score, existing?.bestScore ?: 0),
                lastPlayedEpochMillis = System.currentTimeMillis()
            )
        )
        return stars
    }
}
