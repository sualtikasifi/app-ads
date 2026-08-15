package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [pathDataJson] holds the serialized List<List<DrawingPoint>> for one word's
 * drawing — a vector stroke list rather than a bitmap, so it's cheap to store
 * and can be replayed/re-rendered later (see DrawingPathSerializer).
 */
@Entity(tableName = "drawing_results")
data class DrawingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val wordId: Int,
    val pathDataJson: String,
    val isCorrect: Boolean,
    val userAnswer: String,
    val responseTimeMs: Long,
    val pointsAwarded: Int
)
