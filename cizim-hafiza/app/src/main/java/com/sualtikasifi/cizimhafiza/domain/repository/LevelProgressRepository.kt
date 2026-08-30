package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.LevelProgress
import kotlinx.coroutines.flow.Flow

interface LevelProgressRepository {
    fun observeAllProgress(): Flow<List<LevelProgress>>
    fun observeWorldProgress(worldId: Int): Flow<List<LevelProgress>>

    /** Records a finished level attempt (best stars/score kept via max) and returns this attempt's star count. */
    suspend fun recordLevelResult(worldId: Int, levelIndex: Int, correctCount: Int, totalWords: Int, score: Int): Int
}
