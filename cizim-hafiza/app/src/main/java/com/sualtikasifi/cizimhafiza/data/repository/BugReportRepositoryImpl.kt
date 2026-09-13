package com.sualtikasifi.cizimhafiza.data.repository

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.BugReport
import com.sualtikasifi.cizimhafiza.domain.model.BugReportCategory
import com.sualtikasifi.cizimhafiza.domain.model.BugReportEntry
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Firestore layout: bugReports/{autoId} — one doc per submission, write-only
 * (see firestore.rules). Device/app info is captured automatically so a
 * report is useful without asking the player to type it themselves.
 */
class BugReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : BugReportRepository {

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("auth-failed")
    }

    override suspend fun submitReport(description: String, category: BugReportCategory): Result<Unit> = runCatching {
        val uid = requireUid()
        val ref = firestore.collection("bugReports").add(
            mapOf(
                // Stamped with the sender so they can read their own report's
                // seen status back (see observeMyReports) — firestore.rules
                // scopes reads to `resource.data.uid == request.auth.uid`, so
                // an unstamped report would be invisible even to its author.
                "uid" to uid,
                "category" to category.name,
                "description" to description.trim().take(MAX_DESCRIPTION_LENGTH),
                "appVersionName" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
                "deviceModel" to Build.MODEL,
                "androidSdk" to Build.VERSION.SDK_INT,
                "submittedAt" to System.currentTimeMillis()
            )
        ).await()
        // Same reasoning as BotTrainingRepositoryImpl.saveTraining: .add()
        // only confirms the write reached the local offline cache, not the
        // server — without this, a weak connection at just the wrong moment
        // would silently lose the report while the app reports success.
        //
        // A SERVER-sourced read of just this one document, rather than
        // waitForPendingWrites(), which blocks on every write queued
        // anywhere in the app (any in-flight duel, room or league write),
        // not just this one — that was the actual cause of "gönderiliyor"
        // sometimes taking far longer than a single small document write
        // should.
        try {
            withTimeout(10_000) { ref.get(Source.SERVER).await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("weak-connection", e)
        }
    }

    override fun observeMyReports(): Flow<List<BugReport>> = firestoreFlow("myBugReports") { emit, onError ->
        val uid = requireUid()
        firestore.collection("bugReports")
            // One equality filter, no server-side ordering: adding an orderBy
            // on a second field makes this need a composite index, and this
            // project's indexes are not deployed. The query then fails and
            // the player is told they have never reported anything.
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                emit(
                    snapshot?.documents.orEmpty().map { doc ->
                        BugReport(
                            id = doc.id,
                            // Defaults to COMPLAINT for any report submitted
                            // before this field existed — never null on
                            // screen rather than a crash on an unrecognized value.
                            category = BugReportCategory.entries
                                .find { it.name == doc.getString("category") }
                                ?: BugReportCategory.COMPLAINT,
                            description = doc.getString("description").orEmpty(),
                            submittedAtMillis = doc.getLong("submittedAt") ?: 0L,
                            seenAtMillis = doc.getLong("seenAtMillis")
                        )
                    }
                        // The ordering and the cap the server used to apply.
                        .sortedByDescending { it.submittedAtMillis }
                        .take(MAX_REPORTS_SHOWN.toInt())
                )
            }
    }

    override suspend fun allReports(limit: Int): Result<List<BugReportEntry>> = runCatching {
        // A single orderBy on one field, so no composite index is involved —
        // see observeMyReports for why that matters here.
        firestore.collection("bugReports")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .map { doc ->
                BugReportEntry(
                    report = BugReport(
                        id = doc.id,
                        category = BugReportCategory.entries
                            .find { it.name == doc.getString("category") }
                            ?: BugReportCategory.COMPLAINT,
                        description = doc.getString("description").orEmpty(),
                        submittedAtMillis = doc.getLong("submittedAt") ?: 0L,
                        seenAtMillis = doc.getLong("seenAtMillis")
                    ),
                    uid = doc.getString("uid").orEmpty(),
                    // Stamped by submitReport. Worth showing: "which build"
                    // and "which phone" are the first two questions any bug
                    // report raises, and asking them back costs a round trip
                    // through a player who has already moved on.
                    appVersionName = doc.getString("appVersionName"),
                    deviceModel = doc.getString("deviceModel")
                )
            }
    }

    override suspend fun markSeen(reportId: String): Result<Unit> = runCatching {
        firestore.collection("bugReports").document(reportId)
            .update("seenAtMillis", System.currentTimeMillis())
            .await()
    }

    override suspend fun deleteReports(reportIds: List<String>): Result<Unit> = runCatching {
        if (reportIds.isEmpty()) return@runCatching
        // One batch rather than one delete per document: a "delete all"
        // tap on twenty reports would otherwise fire twenty separate
        // round trips, and a failure partway through would leave the list
        // in a confusing half-deleted state.
        val batch = firestore.batch()
        for (id in reportIds) {
            batch.delete(firestore.collection("bugReports").document(id))
        }
        batch.commit().await()
    }

    private companion object {
        /** Matches the cap enforced in firestore.rules' bugReports create rule. */
        const val MAX_DESCRIPTION_LENGTH = 2000
        const val MAX_REPORTS_SHOWN = 20L
    }
}
