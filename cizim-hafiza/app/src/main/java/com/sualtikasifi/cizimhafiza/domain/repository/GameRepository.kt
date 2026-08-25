package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Achievement
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DrawingResult
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.Word
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    suspend fun getCategories(): List<String>

    /** [difficulty] null ("Tümü") draws with the DifficultyMix curve, not pure uniform random — see GameRepositoryImpl. */
    suspend fun getRandomWords(count: Int, category: String?, difficulty: Difficulty?): List<Word>

    /** One or more difficulties each with their own exact word count — used by the level map's pure and mixed-difficulty steps. */
    suspend fun getRandomWordsMix(category: String?, mix: Map<Difficulty, Int>): List<Word>

    /** Fetches an exact word list by id, in the same order as [ids] — used to give both players in an online room the identical word sequence. */
    suspend fun getWordsByIds(ids: List<Int>): List<Word>

    /** Persists a finished game (session row + one drawing-result row per word) and returns any achievements newly unlocked by it. */
    suspend fun saveGame(totalScore: Int, results: List<DrawingResult>): List<Achievement>

    /** Persists a finished online match's summary (this player's placement/rank among the room, no per-word drawing rows — those live in Firestore) and returns any achievements newly unlocked by it. */
    suspend fun saveOnlineGameSession(
        totalScore: Int,
        wordCount: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        placement: Int,
        playerCount: Int
    ): List<Achievement>

    fun observeStatistics(): Flow<GameStatistics>
}
