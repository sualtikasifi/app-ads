package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.WordReviewCounts

interface WordReviewRepository {
    suspend fun getNextPendingWord(): Word?
    suspend fun getCounts(): WordReviewCounts
    suspend fun keep(wordId: Int)
    suspend fun delete(wordId: Int)
}
