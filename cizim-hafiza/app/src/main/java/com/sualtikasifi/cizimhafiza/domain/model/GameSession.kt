package com.sualtikasifi.cizimhafiza.domain.model

data class GameSession(
    val id: Long,
    val dateEpochMillis: Long,
    val totalScore: Int,
    val wordCount: Int,
    val correctCount: Int,
    val fastestCorrectMs: Long?
)

data class GameStatistics(
    val sessions: List<GameSession>,
    val bestScore: Int,
    val totalWordsPlayed: Int
)
