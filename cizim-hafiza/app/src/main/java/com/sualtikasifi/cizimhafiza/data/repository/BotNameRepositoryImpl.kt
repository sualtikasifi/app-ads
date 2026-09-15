package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.domain.model.BotNameEntry
import com.sualtikasifi.cizimhafiza.domain.repository.BotNameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firestore layout: botNames/{autoId} — one doc per curated name. Reviewer-
 * only end to end (see firestore.rules); a non-reviewer's writes are
 * rejected and [observeNames] simply comes back empty for them.
 */
class BotNameRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : BotNameRepository {

    override fun observeNames(): Flow<List<BotNameEntry>> = firestoreFlow("botNames") { emit, onError ->
        firestore.collection("botNames")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                emit(
                    snapshot?.documents.orEmpty().map { doc ->
                        BotNameEntry(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            createdAtMillis = doc.getLong("createdAt") ?: 0L
                        )
                    }
                )
            }
    }

    override suspend fun addName(name: String): Result<Unit> = runCatching {
        firestore.collection("botNames").add(
            mapOf(
                "name" to name.trim().take(MAX_NAME_LENGTH),
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun deleteName(id: String): Result<Unit> = runCatching {
        firestore.collection("botNames").document(id).delete().await()
    }

    private companion object {
        /** Matches the cap enforced in firestore.rules' botNames create/update rule. */
        const val MAX_NAME_LENGTH = 40
    }
}
