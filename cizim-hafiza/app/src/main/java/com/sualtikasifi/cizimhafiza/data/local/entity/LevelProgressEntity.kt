package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgress

@Entity(tableName = "level_progress", primaryKeys = ["worldId", "levelIndex"])
data class LevelProgressEntity(
    val worldId: Int,
    val levelIndex: Int,
    val bestStars: Int,
    val bestScore: Int,
    val lastPlayedEpochMillis: Long
)

fun LevelProgressEntity.toDomain() = LevelProgress(
    worldId = worldId,
    levelIndex = levelIndex,
    bestStars = bestStars,
    bestScore = bestScore,
    lastPlayedEpochMillis = lastPlayedEpochMillis
)
