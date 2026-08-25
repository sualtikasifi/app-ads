package com.sualtikasifi.cizimhafiza.data.repository

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import kotlinx.coroutines.TimeoutCancellationException
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

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    override suspend fun submitReport(description: String): Result<Unit> = runCatching {
        ensureSignedIn()
        firestore.collection("bugReports").add(
            mapOf(
                "description" to description,
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
            throw IllegalStateException("İnternet bağlantısı zayıf, bildirim gönderilemedi", e)
        }
    }
}
