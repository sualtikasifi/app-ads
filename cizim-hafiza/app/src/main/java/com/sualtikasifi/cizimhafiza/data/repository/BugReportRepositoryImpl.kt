package com.sualtikasifi.cizimhafiza.data.repository

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.BugReport
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

    override suspend fun submitReport(description: String): Result<Unit> = runCatching {
        val uid = requireUid()
        firestore.collection("bugReports").add(
            mapOf(
                // Stamped with the sender so they can read the developer's
                // reply back (see observeMyReports) — firestore.rules scopes
                // reads to `resource.data.uid == request.auth.uid`, so an
                // unstamped report would be invisible even to its own author.
                "uid" to uid,
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
        try {
            withTimeout(20_000) { firestore.waitForPendingWrites().await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("weak-connection", e)
        }
    }

    override fun observeMyReports(): Flow<List<BugReport>> = firestoreFlow("myBugReports") { emit, onError ->
        val uid = requireUid()
        firestore.collection("bugReports")
            .whereEqualTo("uid", uid)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(MAX_REPORTS_SHOWN)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                emit(
                    snapshot?.documents.orEmpty().map { doc ->
                        BugReport(
                            id = doc.id,
                            description = doc.getString("description").orEmpty(),
                            submittedAtMillis = doc.getLong("submittedAt") ?: 0L,
                            reply = doc.getString("reply"),
                            repliedAtMillis = doc.getLong("repliedAt")
                        )
                    }
                )
            }
    }

    private companion object {
        /** Matches the cap enforced in firestore.rules' bugReports create rule. */
        const val MAX_DESCRIPTION_LENGTH = 2000
        const val MAX_REPORTS_SHOWN = 20L
    }
}
