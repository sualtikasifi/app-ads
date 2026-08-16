package com.sualtikasifi.cizimhafiza.domain.model

/** Lifecycle of a friend-vs-friend online room. */
enum class RoomStatus { WAITING, PLAYING, FINISHED }

data class OnlinePlayer(
    val uid: String,
    val displayName: String,
    val ready: Boolean = false,
    val finished: Boolean = false,
    val left: Boolean = false,
    val totalScore: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val fastestCorrectMs: Long? = null
)

data class OnlineRoom(
    val roomCode: String,
    val hostUid: String,
    val status: RoomStatus,
    val wordCount: Int,
    val category: String?,
    val difficulty: Difficulty?,
    val mode: GameMode,
    val wordIds: List<Int>,
    val players: List<OnlinePlayer>,
    val rematchVotes: Set<String> = emptySet()
)

/** An emoji + short preset phrase sent by one player, seen by the other. */
data class Reaction(
    val uid: String,
    val emoji: String,
    val messageKey: String,
    val sentAtMillis: Long
)
