package com.sualtikasifi.cizimhafiza.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sualtikasifi.cizimhafiza.domain.model.GameSession

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val totalScore: Int,
    val wordCount: Int,
    val correctCount: Int,
    val fastestCorrectMs: Long?,
    // Both null for a solo game; both set for an online match — this
    // player's 1-indexed rank among the room and how many players were in it.
    val placement: Int? = null,
    val playerCount: Int? = null
)

fun GameSessionEntity.toDomain() = GameSession(
    id = id,
    dateEpochMillis = date,
    totalScore = totalScore,
    wordCount = wordCount,
    correctCount = correctCount,
    fastestCorrectMs = fastestCorrectMs,
    placement = placement,
    playerCount = playerCount
)
