package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty

/**
 * A row only ever exists here once a word's difficulty has been manually
 * classified — absence of a row means "still pending" (see
 * DifficultyReviewDao.getNextPendingWord), same design as word_review.
 */
@Entity(tableName = "difficulty_review", primaryKeys = ["wordId"])
data class DifficultyReviewEntity(
    val wordId: Int,
    val difficulty: Difficulty,
    val reviewedAtMillis: Long
)
