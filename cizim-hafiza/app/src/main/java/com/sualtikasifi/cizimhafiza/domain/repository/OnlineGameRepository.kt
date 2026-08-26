package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import kotlinx.coroutines.flow.Flow

/** Firestore-backed friend-vs-friend online room: create/join, live room state, results, reactions. */
interface OnlineGameRepository {

    /** This device's Firebase uid, once anonymous sign-in has completed (see CizimHafizaApp). */
    val currentUid: String?

    /** Creates a new room with a fresh room code, returned on success. */
    suspend fun createRoom(
        displayName: String,
        wordCount: Int,
        category: String?,
        difficulty: Difficulty?,
        mode: GameMode
    ): Result<String>

    /**
     * Joins a room that's WAITING (normal case) or already PLAYING (joins
     * as pendingNextRound — see OnlinePlayer — sitting out the round in
     * progress). Rejects with [RoomAlreadyStartedException] only for the
     * brief FINISHED transition window, [RoomFullException] over capacity,
     * or [KickedFromRoomException] if the host kicked this device and the
     * ban hasn't expired/been lifted yet.
     */
    suspend fun joinRoom(roomCode: String, displayName: String): Result<Unit>

    /** Live room state — join status, ready flags, scores once finished. Null once the room stops existing. */
    fun observeRoom(roomCode: String): Flow<OnlineRoom?>

    suspend fun setReady(roomCode: String, ready: Boolean)

    /** Host-only: locks in the shared word list and flips the room to PLAYING. */
    suspend fun startGame(roomCode: String, wordIds: List<Int>)

    /** Called once when a player finishes their own drawing+guessing run. */
    suspend fun submitResult(
        roomCode: String,
        totalScore: Int,
        correctCount: Int,
        wrongCount: Int,
        fastestCorrectMs: Long?,
        items: List<ResultItem>
    )

    suspend fun getPlayerResultItems(roomCode: String, uid: String): List<ResultItem>

    suspend fun voteRematch(roomCode: String)

    /** Once both players voted rematch: picks a new word list and reopens the room for play. */
    suspend fun resetForRematch(roomCode: String, wordIds: List<Int>)

    suspend fun leaveRoom(roomCode: String)

    /**
     * Marks this device as still in [roomCode]. Called on a timer from the
     * waiting room — see OnlinePlayer.isPresent for why a lobby can't rely on
     * [leaveRoom] alone to know who's still there.
     *
     * No-ops when this uid isn't in the room's player map, so a heartbeat can
     * never resurrect a pruned player as a half-written entry.
     */
    suspend fun touchPresence(roomCode: String)

    /** True when this device is still listed in [roomCode]'s player map. */
    suspend fun isStillInRoom(roomCode: String): Boolean

    fun observeReactions(roomCode: String): Flow<List<Reaction>>

    suspend fun sendReaction(roomCode: String, emoji: String, messageKey: String)

    /** Host-only: removes [targetUid] from the room and bans them from rejoining for 30 minutes (see [unbanPlayer]). */
    suspend fun kickPlayer(roomCode: String, targetUid: String, targetDisplayName: String): Result<Unit>

    /** Host-only: lifts an active kick ban early. */
    suspend fun unbanPlayer(roomCode: String, targetUid: String): Result<Unit>

    /**
     * Forces a FINISHED room straight back to WAITING with every player
     * reset to a fresh lobby state (ready=false, pendingNextRound=false,
     * scores cleared) — no rematch vote needed. Used when someone joined
     * mid-round: instead of an instant rematch, the whole group (finishers
     * + the pending joiner) reconvenes in the lobby together.
     */
    suspend fun returnToWaitingRoom(roomCode: String)
}

class RoomNotFoundException : Exception("Oda bulunamadı")
class RoomFullException : Exception("Oda dolu")
class RoomAlreadyStartedException : Exception("Oyun zaten başladı")
class KickedFromRoomException(val remainingMinutes: Int) : Exception("Bu odadan atıldın")
