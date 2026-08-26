package com.sualtikasifi.cizimhafiza.domain.model

/** Lifecycle of a friend-vs-friend online room. */
enum class RoomStatus { WAITING, PLAYING, FINISHED }

/**
 * How long a player can go without checking in before the lobby treats them
 * as gone. [OnlinePlayer.left] only covers a *clean* exit (see
 * OnlineGameRepositoryImpl.leaveRoom); an app that's force-closed, crashes or
 * loses the network never sets it, and the stale entry then sits in the lobby
 * forever — visible as a phantom player, and worse, permanently un-ready, so
 * "everyone is ready" never becomes true and the match can never start.
 * Presence is what actually catches that.
 */
const val PRESENCE_TIMEOUT_MS = 75_000L

data class OnlinePlayer(
    val uid: String,
    val displayName: String,
    val ready: Boolean = false,
    val finished: Boolean = false,
    val left: Boolean = false,
    // Wall-clock of this player's last check-in (see
    // OnlineGameRepository.touchPresence). Null for entries written before
    // presence existed — [joinedAt] stands in for those.
    val lastSeenAt: Long? = null,
    val joinedAt: Long = 0L,
    val totalScore: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val fastestCorrectMs: Long? = null,
    // True when this player joined while the room was already PLAYING —
    // they sit out the round already in progress (no auto-navigate into
    // the Game screen, excluded from the "has everyone finished" check)
    // and wait in the lobby. Reset to false whenever the room returns to
    // WAITING, at which point they're a normal candidate for the next
    // round like everyone else.
    val pendingNextRound: Boolean = false
) {
    /** Still in the room: left cleanly, or simply stopped checking in. */
    fun isPresent(now: Long = System.currentTimeMillis()): Boolean =
        !left && now - (lastSeenAt ?: joinedAt) <= PRESENCE_TIMEOUT_MS
}

/** One host-issued kick ban still in effect — see OnlineRoom.kickedUsers. */
data class KickedUser(
    val uid: String,
    val displayName: String,
    val untilMillis: Long
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
    // Wall-clock time this round's status last flipped to PLAYING (see
    // OnlineGameRepositoryImpl.startGame/resetForRematch, BotRoomEngine).
    // Lets a pendingNextRound joiner sitting in the lobby show an estimated
    // "time left" for the round in progress instead of no information at
    // all — see WaitingRoomScreen.
    val startedAt: Long? = null,
    val rematchVotes: Set<String> = emptySet(),
    // Host-only feature (see WaitingRoomViewModel.kickPlayer/unbanPlayer) —
    // enforced for real in firestore.rules' rooms/{roomCode} update rule,
    // this is just what the UI reads to show remaining time / an "Affet"
    // (unban) list.
    val kickedUsers: List<KickedUser> = emptyList()
)

/** An emoji + short preset phrase sent by one player, seen by the other. */
data class Reaction(
    val uid: String,
    val emoji: String,
    val messageKey: String,
    val sentAtMillis: Long
)
