package com.sualtikasifi.cizimhafiza.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReport
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReportReason
import com.sualtikasifi.cizimhafiza.domain.model.DrawingReports
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.repository.DrawingReportRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Firestore layout: drawingReports/{scopeKey__reporterUid} — one document per
 * (drawing, reporter) pair, created and never edited. See firestore.rules for
 * the create/read enforcement these writes are shaped to match key for key,
 * and DrawingReports for why the id is built rather than generated.
 */
class DrawingReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : DrawingReportRepository {

    private val reports get() = firestore.collection("drawingReports")
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("auth-failed")
    }

    override suspend fun reportRunDrawing(
        runId: String,
        reportedUid: String,
        word: String,
        strokes: List<DrawingStroke>,
        reason: DrawingReportReason
    ): Result<Unit> = submit(
        scopeKey = DrawingReports.scopeKeyForRun(runId),
        runId = runId,
        reportedUid = reportedUid,
        word = word,
        strokes = strokes,
        reason = reason
    )

    override suspend fun reportRoomDrawing(
        roomCode: String,
        reportedUid: String,
        word: String,
        strokes: List<DrawingStroke>,
        reason: DrawingReportReason
    ): Result<Unit> = submit(
        scopeKey = DrawingReports.scopeKeyForRoom(roomCode, reportedUid),
        // Deliberately blank: an online drawing belongs to no recorded round,
        // and stamping one here would make isRetired() count it against a
        // round it has nothing to do with.
        runId = "",
        reportedUid = reportedUid,
        word = word,
        strokes = strokes,
        reason = reason
    )

    private suspend fun submit(
        scopeKey: String,
        runId: String,
        reportedUid: String,
        word: String,
        strokes: List<DrawingStroke>,
        reason: DrawingReportReason
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val strokesJson = json.encodeToString(strokes)
        // A drawing this large cannot be rendered in review and the rules
        // would refuse it anyway; dropping the evidence is better than
        // failing the report, since the reason and the reported player are
        // the parts that actually accumulate.
        val evidence = if (strokesJson.length <= MAX_STROKES_JSON) strokesJson else ""
        reports.document(DrawingReports.idFor(scopeKey, uid)).set(
            mapOf(
                "reporterUid" to uid,
                "reportedUid" to reportedUid,
                "runId" to runId,
                "reason" to reason.name,
                "word" to word.take(MAX_WORD_LENGTH),
                "strokesJson" to evidence,
                "submittedAt" to System.currentTimeMillis()
            )
        ).await()
        // Same reasoning as BugReportRepositoryImpl: set() only confirms the
        // write reached the offline cache. A report that never left the phone
        // is a report that never counted towards retiring anything.
        try {
            withTimeout(WRITE_TIMEOUT_MS) { firestore.waitForPendingWrites().await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("weak-connection", e)
        }
    }

    /**
     * One small query on the way into a match.
     *
     * Failure is deliberately answered with false. This runs while a player
     * is waiting for an opponent, and refusing to match anybody because a
     * count could not be read would turn a network hiccup into an empty
     * screen — the exact failure this whole mode exists to avoid.
     */
    override suspend fun isRetired(runId: String): Boolean = runCatching {
        if (runId.isBlank()) return false
        reports.whereEqualTo("runId", runId)
            .limit(DrawingReports.RETIREMENT_QUERY_LIMIT.toLong())
            .get()
            .await()
            .size() >= DrawingReports.REPORTS_TO_RETIRE
    }.onFailure { Log.w(TAG, "Retirement check failed for $runId", it) }.getOrDefault(false)

    override suspend fun recentReports(limit: Int): Result<List<DrawingReport>> = runCatching {
        reports.orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .map { doc ->
                DrawingReport(
                    id = doc.id,
                    // Anything unrecognised reads as MEANINGLESS rather than
                    // throwing: a report from a newer build than this one is
                    // still worth showing.
                    reason = DrawingReportReason.entries
                        .find { it.name == doc.getString("reason") }
                        ?: DrawingReportReason.MEANINGLESS,
                    reportedUid = doc.getString("reportedUid").orEmpty(),
                    runId = doc.getString("runId").orEmpty(),
                    word = doc.getString("word").orEmpty(),
                    strokesJson = doc.getString("strokesJson").orEmpty(),
                    submittedAtMillis = doc.getLong("submittedAt") ?: 0L
                )
            }
    }

    private companion object {
        const val TAG = "DrawingReports"
        const val WRITE_TIMEOUT_MS = 20_000L

        /** Comfortably above one word's strokes, far below anything worth abusing as storage. */
        const val MAX_STROKES_JSON = 60_000
        const val MAX_WORD_LENGTH = 64
    }
}
