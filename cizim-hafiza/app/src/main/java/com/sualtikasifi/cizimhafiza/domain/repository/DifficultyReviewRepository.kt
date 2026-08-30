package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DifficultyReviewCounts
import com.sualtikasifi.cizimhafiza.domain.model.Word

interface DifficultyReviewRepository {
    suspend fun getNextPendingWord(): Word?
    suspend fun getCounts(): DifficultyReviewCounts
    suspend fun setDifficulty(wordId: Int, difficulty: Difficulty)

    /** All classification decisions made so far, as a JSON string ready to hand off — see DifficultyReviewShareUtil. */
    suspend fun exportReviewedDifficultiesJson(): String
}
