package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.WordReviewCounts

interface WordReviewRepository {
    suspend fun getNextPendingWord(): Word?
    suspend fun getCounts(): WordReviewCounts
    suspend fun keep(wordId: Int)
    suspend fun delete(wordId: Int)

    /** All decisions made so far (Kalsın + Sil), as a JSON string ready to hand off — see WordReviewShareUtil. */
    suspend fun exportReviewedWordsJson(): String
}
