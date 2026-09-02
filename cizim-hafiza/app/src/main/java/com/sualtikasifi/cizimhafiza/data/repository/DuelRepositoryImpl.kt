package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.sualtikasifi.cizimhafiza.domain.model.Duel
import com.sualtikasifi.cizimhafiza.domain.model.DuelStatus
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.repository.DuelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Firestore layout: a single top-level `duels/{duelId}` collection (not
 * nested under a user, since a duel has two participants — see
 * firestore.rules for the actual create/update/read enforcement, which
 * this repository's writes are shaped to match exactly, key for key).
 */
class DuelRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : DuelRepository {

    private val duels get() = firestore.collection("duels")
    private val json = Json { ignoreUnknownKeys = true }

    override val currentUid: String? get() = auth.currentUser?.uid

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun createDuel(
        opponentUid: String,
        opponentName: String,
        items: List<ResultItem>,
        challengerScore: Int,
        challengerCorrectCount: Int
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        // Cache-first: this is the player's OWN profile doc, written by this
        // device (see FriendRepositoryImpl.publishWeeklyScore/ensureFriendCode)
        // and read again on every duel sent. A server read per duel bought
        // nothing — the cached copy cannot be staler than this device's own
        // last write of it.
        val meDoc = firestore.collection("users").document(uid)
        val challengerName = (
            runCatching { meDoc.get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }
                ?: meDoc.get(Source.SERVER).await()
            ).getString("nickname").orEmpty().ifBlank { "Oyuncu" }
        duels.add(
            mapOf(
                "challengerUid" to uid,
                "challengerName" to challengerName,
                "opponentUid" to opponentUid,
                "opponentName" to opponentName,
                "itemsJson" to json.encodeToString(items),
                "challengerScore" to challengerScore,
                "challengerCorrectCount" to challengerCorrectCount,
                "opponentScore" to null,
                "opponentCorrectCount" to null,
                "status" to DuelStatus.AWAITING_OPPONENT.name,
                "createdAt" to System.currentTimeMillis(),
                "completedAt" to null,
                // The challenger created it, so there is nothing about their
                // own duel they haven't already seen.
                "seenByChallenger" to true
            )
        ).await()
        Unit
    }

    override suspend fun getDuel(duelId: String): Result<Duel?> = runCatching {
        duels.document(duelId).get().await().toDuel()
    }

    override fun observeIncomingDuels(): Flow<List<Duel>> =
        firestoreFlow("duels:incoming") { emit, onError ->
            val uid = auth.currentUser?.uid
            if (uid == null) {
                emit(emptyList())
                // No listener to actually attach — an empty registration
                // that removes cleanly, matching the shape firestoreFlow expects.
                return@firestoreFlow duels.limit(0).addSnapshotListener { _, _ -> }
            }
            duels
                .whereEqualTo("opponentUid", uid)
                .whereEqualTo("status", DuelStatus.AWAITING_OPPONENT.name)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                // Bounded like the sent list: an unbounded listener re-reads
                // every awaiting duel ever sent to this player on each
                // attach, and the screen only ever shows the newest anyway.
                .limit(SENT_DUELS_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        return@addSnapshotListener
                    }
                    emit(snapshot?.documents.orEmpty().mapNotNull { it.toDuel() })
                }
        }

    override fun observeSentDuels(): Flow<List<Duel>> =
        firestoreFlow("duels:sent") { emit, onError ->
            val uid = auth.currentUser?.uid
            if (uid == null) {
                emit(emptyList())
                return@firestoreFlow duels.limit(0).addSnapshotListener { _, _ -> }
            }
            duels
                .whereEqualTo("challengerUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(SENT_DUELS_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        return@addSnapshotListener
                    }
                    emit(snapshot?.documents.orEmpty().mapNotNull { it.toDuel() })
                }
        }

    override suspend fun submitDuelResult(duelId: String, opponentScore: Int, opponentCorrectCount: Int): Result<Unit> = runCatching {
        duels.document(duelId).update(
            mapOf(
                "status" to DuelStatus.COMPLETE.name,
                "opponentScore" to opponentScore,
                "opponentCorrectCount" to opponentCorrectCount,
                "completedAt" to System.currentTimeMillis(),
                "seenByChallenger" to false
            )
        ).await()
        Unit
    }

    override suspend fun markSeenByChallenger(duelId: String): Result<Unit> = runCatching {
        // See firestore.rules' duels/{duelId} update rule's second branch —
        // the challenger's one and only allowed write, on a COMPLETE duel,
        // touching only this field.
        duels.document(duelId).update("seenByChallenger", true).await()
        Unit
    }

    override suspend fun deleteDuel(duelId: String): Result<Unit> = runCatching {
        duels.document(duelId).delete().await()
        Unit
    }

    private fun DocumentSnapshot.toDuel(): Duel? {
        val challengerUid = getString("challengerUid") ?: return null
        val opponentUid = getString("opponentUid") ?: return null
        val itemsJson = getString("itemsJson") ?: return null
        val items = runCatching { json.decodeFromString<List<ResultItem>>(itemsJson) }.getOrDefault(emptyList())
        val status = getString("status")?.let { runCatching { DuelStatus.valueOf(it) }.getOrNull() }
            ?: DuelStatus.AWAITING_OPPONENT
        return Duel(
            id = id,
            challengerUid = challengerUid,
            challengerName = getString("challengerName").orEmpty(),
            opponentUid = opponentUid,
            opponentName = getString("opponentName").orEmpty(),
            items = items,
            challengerScore = (get("challengerScore") as? Number)?.toInt() ?: 0,
            challengerCorrectCount = (get("challengerCorrectCount") as? Number)?.toInt() ?: 0,
            opponentScore = (get("opponentScore") as? Number)?.toInt(),
            opponentCorrectCount = (get("opponentCorrectCount") as? Number)?.toInt(),
            status = status,
            createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L,
            completedAt = (get("completedAt") as? Number)?.toLong(),
            seenByChallenger = getBoolean("seenByChallenger") ?: true
        )
    }

    private companion object {
        // "Did they beat me" is only interesting recently — an unbounded
        // list of every duel ever sent would grow without limit for a
        // player who challenges friends often.
        const val SENT_DUELS_LIMIT = 30L
    }
}
