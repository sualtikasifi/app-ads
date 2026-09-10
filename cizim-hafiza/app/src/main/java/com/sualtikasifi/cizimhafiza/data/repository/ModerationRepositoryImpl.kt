package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.domain.model.Moderation
import com.sualtikasifi.cizimhafiza.domain.model.PendingRun
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.RunPage
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
 *  - `moderationStrikes/{uid}` — lifetime rejections. Never cleared: see
 *    Moderation.STRIKES_BEFORE_LOCKOUT.
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

    override suspend fun pendingRuns(limit: Int, after: Long?): Result<RunPage> = runCatching {
        // Oldest first: the queue is a backlog, and the round that has waited
        // longest is the one keeping somebody out of the pool.
        read(pendingRuns, pendingRunItems, limit, Query.Direction.ASCENDING, after)
    }

    override suspend fun poolRuns(limit: Int, after: Long?): Result<RunPage> = runCatching {
        // Newest first: the pool is not a backlog, so what is worth seeing is
        // what most recently got in.
        read(ghostRuns, ghostRunItems, limit, Query.Direction.DESCENDING, after)
    }

    /**
     * Reads a run collection and its sibling drawings collection into the
     * shape the review screen shows.
     *
     * Shared by the queue and the pool because the two hold identical
     * documents — a pool run is a queue run that was copied across verbatim.
     */
    private suspend fun read(
        runs: CollectionReference,
        items: CollectionReference,
        limit: Int,
        direction: Query.Direction,
        after: Long?
    ): RunPage {
        val ordered = runs.orderBy("createdAt", direction)
        // startAfter on the ordering field rather than a document snapshot:
        // the cursor has to travel up into UI state, and a raw millisecond is
        // the only form of it that can.
        val page = if (after == null) ordered else ordered.startAfter(after)
        val docs = page
            .limit(limit.toLong())
            .get()
            .await()
            .documents

        val decoded = docs.mapNotNull { doc ->
            // The drawings live in their own document (they are ~100x the
            // size of the round), so the queue costs two reads a row. Worth
            // it: a row without its drawings cannot be judged, which is the
            // entire point of the screen.
            val itemsDoc = runCatching { items.document(doc.id).get().await() }.getOrNull()
            val decoded = runCatching {
                json.decodeFromString<List<ResultItem>>(itemsDoc?.getString("itemsJson").orEmpty())
            }.getOrDefault(emptyList())
            // A round whose drawings did not survive cannot be reviewed, so it
            // is not offered — it will be cleaned up with the rest of the
            // queue rather than sitting here unreviewable forever.
            if (decoded.isEmpty()) return@mapNotNull null
            PendingRun(
                id = doc.id,
                uid = doc.getString("uid").orEmpty(),
                nickname = doc.getString("nickname").orEmpty(),
                level = (doc.getLong("level") ?: 1L).toInt(),
                items = decoded,
                totalScore = (doc.getLong("totalScore") ?: 0L).toInt(),
                correctCount = (doc.getLong("correctCount") ?: 0L).toInt(),
                xpEarned = (doc.getLong("xpEarned") ?: 0L).toInt(),
                createdAtMillis = doc.getLong("createdAt") ?: 0L
            )
        }

        return RunPage(
            runs = decoded,
            // Taken from the last DOCUMENT READ, not the last row returned: a
            // row can be dropped above for having no drawings, and cursoring
            // from the last surviving row would read it again forever.
            nextCursor = docs.lastOrNull()?.getLong("createdAt"),
            // Short page means the collection ended. A page that is full but
            // decoded to nothing is not the end — it just had nothing worth
            // showing, and the next page still has to be asked for.
            endReached = docs.size < limit
        )
    }

    override suspend fun approve(runId: String): Result<Unit> = runCatching {
        val runDoc = pendingRuns.document(runId).get().await()
        val data = runDoc.data ?: error("Pending run $runId has no data")
        val itemsDoc = pendingRunItems.document(runId).get().await()
        val itemsData = itemsDoc.data ?: error("Pending run $runId has no drawings")

        // One batch: the run, its drawings and the removal of the queue entry
        // all land together, so the pool can never hold a run whose drawings
        // stayed behind — the same reasoning that made the original write a
        // batch.
        val batch = firestore.batch()
        batch.set(ghostRuns.document(runId), data)
        batch.set(ghostRunItems.document(runId), itemsData)
        batch.delete(pendingRuns.document(runId))
        batch.delete(pendingRunItems.document(runId))
        // The author's strike count is deliberately left alone. An approval
        // says this round was fine, not that the earlier offences did not
        // happen — clearing it would let somebody alternate a cheated round
        // with an honest one and never reach a lockout.
        batch.commit().await()
    }

    override suspend fun rename(runId: String, nickname: String, inPool: Boolean): Result<Unit> =
        runCatching {
            val trimmed = nickname.trim()
            require(trimmed.isNotEmpty()) { "A run cannot be renamed to nothing" }
            // A single-field update, not a rewrite of the document: the score,
            // the words and the timestamp are what the round IS, and a rename
            // is not allowed to disturb any of them.
            val collection = if (inPool) ghostRuns else pendingRuns
            collection.document(runId).update("nickname", trimmed).await()
        }

    override suspend fun sendBackToQueue(runId: String): Result<Unit> = runCatching {
        val runDoc = ghostRuns.document(runId).get().await()
        val data = runDoc.data ?: error("Pool run $runId has no data")
        val itemsDoc = ghostRunItems.document(runId).get().await()
        val itemsData = itemsDoc.data ?: error("Pool run $runId has no drawings")

        // The exact mirror of approve(), one batch for the same reason: the
        // round must never exist in both places at once, or a player could be
        // matched against a run that is supposedly awaiting review.
        val batch = firestore.batch()
        batch.set(pendingRuns.document(runId), data)
        batch.set(pendingRunItems.document(runId), itemsData)
        batch.delete(ghostRuns.document(runId))
        batch.delete(ghostRunItems.document(runId))
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
        // Every third offence, not "three or more": the count never resets,
        // so `>=` would lock the account out on every offence from the third
        // onwards.
        val lockedUntil = if (strike % Moderation.STRIKES_BEFORE_LOCKOUT == 0) {
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
                // Seeded at zero rather than left absent: the offending device
                // finds its outstanding penalties with an equality on this
                // field, and Firestore cannot ask for "missing".
                "appliedAt" to 0L,
                "createdAt" to System.currentTimeMillis()
            )
        )
        batch.set(
            strikes.document(uid),
            mapOf(
                "count" to strike.toLong(),
                "updatedAt" to System.currentTimeMillis()
            )
        )
        batch.commit().await()
    }

    /** Present so the reviewer's own uid is available if a rule ever needs it. */
    @Suppress("unused")
    private fun reviewerUid(): String? = auth.currentUser?.uid
}
