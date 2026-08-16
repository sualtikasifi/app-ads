package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.OnlinePlayer
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.RoomAlreadyStartedException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomFullException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomNotFoundException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Firestore layout (kept intentionally flat/explicit — hand-written map
 * read/write instead of Firestore's POJO reflection mapping, since this
 * repository can't be exercised against a live project in this environment
 * and explicit code is far easier to get right without that safety net):
 *
 * rooms/{roomCode}                     — small, frequently-read lobby/progress doc
 * rooms/{roomCode}/results/{uid}       — heavy drawing-strokes payload, written once at finish
 * rooms/{roomCode}/reactions/{autoId}  — emoji/preset-message stream
 */
class OnlineGameRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : OnlineGameRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val rooms get() = firestore.collection("rooms")

    override val currentUid: String? get() = auth.currentUser?.uid

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun createRoom(
        displayName: String,
        wordCount: Int,
        category: String?,
        difficulty: Difficulty?,
        mode: GameMode
    ): Result<String> = runCatching {
        val uid = requireUid()
        repeat(5) {
            val code = generateRoomCode()
            val docRef = rooms.document(code)
            val created = firestore.runTransaction<Boolean> { tx ->
                val snapshot = tx.get(docRef)
                if (snapshot.exists()) {
                    false
                } else {
                    val data = hashMapOf(
                        "hostUid" to uid,
                        "createdAt" to System.currentTimeMillis(),
                        "status" to RoomStatus.WAITING.name,
                        "wordCount" to wordCount.toLong(),
                        "category" to category,
                        "difficulty" to difficulty?.name,
                        "mode" to mode.name,
                        "wordIds" to emptyList<Long>(),
                        "players" to mapOf(uid to playerMap(displayName)),
                        "rematchVotes" to emptyList<String>()
                    )
                    tx.set(docRef, data)
                    true
                }
            }.await()
            if (created) return@runCatching code
        }
        throw IllegalStateException("Oda kodu oluşturulamadı, tekrar dene")
    }

    override suspend fun joinRoom(roomCode: String, displayName: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val docRef = rooms.document(roomCode)
        firestore.runTransaction<Unit> { tx ->
            val snapshot = tx.get(docRef)
            if (!snapshot.exists()) throw RoomNotFoundException()
            val status = snapshot.getString("status")
            @Suppress("UNCHECKED_CAST")
            val players = snapshot.get("players") as? Map<String, Any?> ?: emptyMap()
            if (!players.containsKey(uid)) {
                if (players.size >= 2) throw RoomFullException()
                if (status != RoomStatus.WAITING.name) throw RoomAlreadyStartedException()
                tx.update(docRef, "players.$uid", playerMap(displayName))
            }
        }.await()
    }

    override fun observeRoom(roomCode: String): Flow<OnlineRoom?> = callbackFlow {
        val registration = rooms.document(roomCode).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toOnlineRoom())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setReady(roomCode: String, ready: Boolean) {
        val uid = requireUid()
        rooms.document(roomCode).update("players.$uid.ready", ready).await()
    }

    override suspend fun startGame(roomCode: String, wordIds: List<Int>) {
        rooms.document(roomCode).update(
            mapOf(
                "status" to RoomStatus.PLAYING.name,
                "wordIds" to wordIds.map { it.toLong() }
            )
        ).await()
    }

    override suspend fun submitResult(
        roomCode: String,
        totalScore: Int,
        correctCount: Int,
        wrongCount: Int,
        fastestCorrectMs: Long?,
        items: List<ResultItem>
    ) {
        val uid = requireUid()
        val docRef = rooms.document(roomCode)
        docRef.update(
            mapOf(
                "players.$uid.finished" to true,
                "players.$uid.totalScore" to totalScore.toLong(),
                "players.$uid.correctCount" to correctCount.toLong(),
                "players.$uid.wrongCount" to wrongCount.toLong(),
                "players.$uid.fastestCorrectMs" to fastestCorrectMs
            )
        ).await()

        docRef.collection("results").document(uid)
            .set(mapOf("itemsJson" to json.encodeToString(items)))
            .await()

        // Last one to finish flips the room to FINISHED for both clients.
        val room = docRef.get().await().toOnlineRoom()
        if (room != null && room.players.isNotEmpty() && room.players.all { it.finished }) {
            docRef.update("status", RoomStatus.FINISHED.name).await()
        }
    }

    override suspend fun getPlayerResultItems(roomCode: String, uid: String): List<ResultItem> {
        val snapshot = rooms.document(roomCode).collection("results").document(uid).get().await()
        val itemsJson = snapshot.getString("itemsJson") ?: return emptyList()
        return runCatching { json.decodeFromString<List<ResultItem>>(itemsJson) }.getOrDefault(emptyList())
    }

    override suspend fun voteRematch(roomCode: String) {
        val uid = requireUid()
        rooms.document(roomCode).update("rematchVotes", FieldValue.arrayUnion(uid)).await()
    }

    override suspend fun resetForRematch(roomCode: String, wordIds: List<Int>) {
        val docRef = rooms.document(roomCode)
        val room = docRef.get().await().toOnlineRoom() ?: return
        val resetPlayers = room.players.associate { player -> player.uid to playerMap(player.displayName) }
        docRef.update(
            mapOf(
                "status" to RoomStatus.PLAYING.name,
                "wordIds" to wordIds.map { it.toLong() },
                "players" to resetPlayers,
                "rematchVotes" to emptyList<String>()
            )
        ).await()
    }

    override suspend fun leaveRoom(roomCode: String) {
        val uid = requireUid()
        rooms.document(roomCode).update("players.$uid.left", true).await()
    }

    override fun observeReactions(roomCode: String): Flow<List<Reaction>> = callbackFlow {
        val registration = rooms.document(roomCode).collection("reactions")
            .orderBy("sentAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val reactions = snapshot?.documents?.mapNotNull { doc ->
                    val reactionUid = doc.getString("uid") ?: return@mapNotNull null
                    val emoji = doc.getString("emoji") ?: return@mapNotNull null
                    Reaction(
                        uid = reactionUid,
                        emoji = emoji,
                        messageKey = doc.getString("messageKey") ?: "",
                        sentAtMillis = doc.getLong("sentAt") ?: 0L
                    )
                } ?: emptyList()
                trySend(reactions)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendReaction(roomCode: String, emoji: String, messageKey: String) {
        val uid = requireUid()
        rooms.document(roomCode).collection("reactions").add(
            mapOf(
                "uid" to uid,
                "emoji" to emoji,
                "messageKey" to messageKey,
                "sentAt" to System.currentTimeMillis()
            )
        ).await()
    }

    private fun playerMap(displayName: String) = mapOf(
        "displayName" to displayName,
        "joinedAt" to System.currentTimeMillis(),
        "ready" to false,
        "finished" to false,
        "left" to false,
        "totalScore" to 0L,
        "correctCount" to 0L,
        "wrongCount" to 0L,
        "fastestCorrectMs" to null
    )

    private fun generateRoomCode(): String = (100000..999999).random().toString()

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toOnlineRoom(): OnlineRoom? {
        if (!exists()) return null
        val hostUid = getString("hostUid") ?: return null
        val status = getString("status")?.let { runCatching { RoomStatus.valueOf(it) }.getOrNull() }
            ?: RoomStatus.WAITING
        val wordCount = (get("wordCount") as? Number)?.toInt() ?: 10
        val category = getString("category")
        val difficulty = getString("difficulty")?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
        val mode = getString("mode")?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.NORMAL
        val wordIds = (get("wordIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val playersMap = get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
        val players = playersMap.map { (uid, data) ->
            OnlinePlayer(
                uid = uid,
                displayName = data["displayName"] as? String ?: "",
                ready = data["ready"] as? Boolean ?: false,
                finished = data["finished"] as? Boolean ?: false,
                left = data["left"] as? Boolean ?: false,
                totalScore = (data["totalScore"] as? Number)?.toInt() ?: 0,
                correctCount = (data["correctCount"] as? Number)?.toInt() ?: 0,
                wrongCount = (data["wrongCount"] as? Number)?.toInt() ?: 0,
                fastestCorrectMs = (data["fastestCorrectMs"] as? Number)?.toLong()
            )
        }
        val rematchVotes = (get("rematchVotes") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
        return OnlineRoom(
            roomCode = id,
            hostUid = hostUid,
            status = status,
            wordCount = wordCount,
            category = category,
            difficulty = difficulty,
            mode = mode,
            wordIds = wordIds,
            players = players,
            rematchVotes = rematchVotes
        )
    }
}
