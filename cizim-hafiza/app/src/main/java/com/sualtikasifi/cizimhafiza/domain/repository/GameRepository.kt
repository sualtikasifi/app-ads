package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.Word
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    suspend fun getCategories(): List<String>
    suspend fun getRandomWords(count: Int, category: String?): List<Word>

    /** Persists a finished game (session row + one drawing-result row per word) and returns the new session id. */
    suspend fun saveGame(totalScore: Int, results: List<DrawingResult>): Long

    fun observeStatistics(): Flow<GameStatistics>
}
