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

    fun observeReactions(roomCode: String): Flow<List<Reaction>>

    suspend fun sendReaction(roomCode: String, emoji: String, messageKey: String)
}

class RoomNotFoundException : Exception("Oda bulunamadı")
class RoomFullException : Exception("Oda dolu")
class RoomAlreadyStartedException : Exception("Oyun zaten başladı")
