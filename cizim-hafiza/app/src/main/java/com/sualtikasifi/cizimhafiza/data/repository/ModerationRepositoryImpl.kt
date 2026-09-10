package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.domain.model.Moderation
import com.sualtikasifi.cizimhafiza.domain.model.PendingRun
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.repository.ModerationRepository
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout:
 *  - `pendingRuns/{id}` + `pendingRunItems/{id}` — the queue, written by
 *    GhostRunRepositoryImpl and emptied only from here.
 *  - `ghostRuns/{id}` + `ghostRunItems/{id}` — the live pool. A run reaches
 *    it by being copied verbatim out of the queue.
 *  - `penalties/{id}` — one per rejected round, applied by the offending
 *    device (see PenaltyRepositoryImpl).
 *  - `moderationStrikes/{uid}` — consecutive rejections, cleared by an
 *    approval.
 *
 * See firestore.rules: the pool is created only by these copies, so a client
 * can no longer write itself into the pool at all.
 */
@Singleton
class ModerationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ModerationRepository {

    private val pendingRuns get() = firestore.collection("pendingRuns")
    private val pendingRunItems get() = firestore.collection("pendingRunItems")
    private val ghostRuns get() = firestore.collection("ghostRuns")
    private val ghostRunItems get() = firestore.collection("ghostRunItems")
    private val penalties get() = firestore.collection("penalties")
    private val strikes get() = firestore.collection("moderationStrikes")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pendingRuns(limit: Int): Result<List<PendingRun>> = runCatching {
        val runs = pendingRuns
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents

        runs.mapNotNull { doc ->
            // The drawings live in their own document (they are ~100x the
            // size of the round), so the queue costs two reads a row. Worth
            // it: a row without its drawings cannot be judged, which is the
            // entire point of the screen.
            val itemsDoc = runCatching { pendingRunItems.document(doc.id).get().await() }.getOrNull()
            val items = runCatching {
                json.decodeFromString<List<ResultItem>>(itemsDoc?.getString("itemsJson").orEmpty())
            }.getOrDefault(emptyList())
            // A round whose drawings did not survive cannot be reviewed, so it
            // is not offered — it will be cleaned up with the rest of the
            // queue rather than sitting here unreviewable forever.
            if (items.isEmpty()) return@mapNotNull null
            PendingRun(
                id = doc.id,
                uid = doc.getString("uid").orEmpty(),
                nickname = doc.getString("nickname").orEmpty(),
                level = (doc.getLong("level") ?: 1L).toInt(),
                items = items,
                totalScore = (doc.getLong("totalScore") ?: 0L).toInt(),
                correctCount = (doc.getLong("correctCount") ?: 0L).toInt(),
                xpEarned = (doc.getLong("xpEarned") ?: 0L).toInt(),
                createdAtMillis = doc.getLong("createdAt") ?: 0L
            )
        }
    }

    override suspend fun approve(runId: String): Result<Unit> = runCatching {
        val runDoc = pendingRuns.document(runId).get().await()
        val data = runDoc.data ?: error("Pending run $runId has no data")
        val itemsDoc = pendingRunItems.document(runId).get().await()
        val itemsData = itemsDoc.data ?: error("Pending run $runId has no drawings")
        val uid = runDoc.getString("uid").orEmpty()

        // One batch: the run, its drawings and the removal of the queue entry
        // all land together, so the pool can never hold a run whose drawings
        // stayed behind — the same reasoning that made the original write a
        // batch.
        val batch = firestore.batch()
        batch.set(ghostRuns.document(runId), data)
        batch.set(ghostRunItems.document(runId), itemsData)
        batch.delete(pendingRuns.document(runId))
        batch.delete(pendingRunItems.document(runId))
        // An approved round says this account is playing properly right now,
        // which is exactly what the consecutive count is asking.
        if (uid.isNotEmpty()) batch.delete(strikes.document(uid))
        batch.commit().await()
    }

    override suspend fun reject(runId: String, xpToRevoke: Int): Result<Unit> = runCatching {
        val runDoc = pendingRuns.document(runId).get().await()
        val uid = runDoc.getString("uid").orEmpty()
        require(uid.isNotEmpty()) { "Pending run $runId has no author" }

        // Read before write rather than in a transaction: this is one person
        // tapping a button on one screen, so there is no second writer to
        // race with, and a transaction here would buy nothing.
        val previous = runCatching {
            (strikes.document(uid).get().await().getLong("count") ?: 0L).toInt()
        }.getOrDefault(0)
        val strike = previous + 1
        val lockedUntil = if (strike >= Moderation.STRIKES_BEFORE_LOCKOUT) {
            // The reviewer's clock, stored absolute. Computing it on the
            // offending device would let somebody sit out a lockout by moving
            // their own clock forward.
            System.currentTimeMillis() + Moderation.LOCKOUT_MILLIS
        } else {
            0L
        }

        val batch = firestore.batch()
        batch.delete(pendingRuns.document(runId))
        batch.delete(pendingRunItems.document(runId))
        batch.set(
            penalties.document(),
            mapOf(
                "uid" to uid,
                "xpRevoked" to xpToRevoke.coerceAtLeast(0).toLong(),
                "strike" to strike.toLong(),
                "lockedUntil" to lockedUntil,
                "createdAt" to System.currentTimeMillis()
            )
        )
        batch.set(
            strikes.document(uid),
            mapOf(
                // Reset to zero rather than left at three: the lockout is the
                // punishment for the third, and carrying the count past it
                // would make every later offence an instant lockout.
                "count" to if (lockedUntil > 0L) 0L else strike.toLong(),
                "updatedAt" to System.currentTimeMillis()
            )
        )
        batch.commit().await()
    }

    /** Present so the reviewer's own uid is available if a rule ever needs it. */
    @Suppress("unused")
    private fun reviewerUid(): String? = auth.currentUser?.uid
}
