package com.sualtikasifi.cizimhafiza.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.BuildConfig
import com.sualtikasifi.cizimhafiza.domain.model.DetectorEvent
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.WrittenWordDetector
import com.sualtikasifi.cizimhafiza.domain.repository.DetectorEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout: detectorEvents/{autoId} — one document per refused
 * round, created and never edited. See firestore.rules.
 */
@Singleton
class DetectorEventRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : DetectorEventRepository {

    private val events get() = firestore.collection("detectorEvents")
    private val json = Json { ignoreUnknownKeys = true }

    // Its own scope for the same reason GhostRunRepositoryImpl has one: this
    // starts as the result screen appears and the player may leave at once,
    // which would cancel a caller's viewModelScope mid-write.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun recordRefusal(
        verdict: WrittenWordDetector.RoundVerdict,
        items: List<ResultItem>
    ) {
        scope.launch {
            runCatching { write(verdict, items) }
                // Swallowed on purpose: this is bookkeeping about a round that
                // is already over and already paid out. A player must never
                // see anything because a telemetry write failed.
                .onFailure { Log.w(TAG, "Detector event not recorded", it) }
        }
    }

    private suspend fun write(
        verdict: WrittenWordDetector.RoundVerdict,
        items: List<ResultItem>
    ) {
        val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: return
        val sample = verdict.mostSuspiciousIndex?.let { items.getOrNull(it) }
        val sampleJson = sample?.let { json.encodeToString(it.strokes) }.orEmpty()
        events.add(
            mapOf(
                "uid" to uid,
                "scores" to verdict.scores.map { (it * 100).toInt().toLong() },
                "flaggedCount" to verdict.flaggedCount.toLong(),
                "wordCount" to items.size.toLong(),
                "outcomeLooksRead" to verdict.outcomeLooksRead,
                "sampleWord" to sample?.word.orEmpty(),
                // Dropped rather than truncated when oversized: a half a
                // drawing renders as nonsense and would be worse than none.
                "sampleStrokesJson" to if (sampleJson.length <= MAX_SAMPLE_JSON) sampleJson else "",
                "appVersionCode" to BuildConfig.VERSION_CODE.toLong(),
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun recentEvents(limit: Int): Result<List<DetectorEvent>> = runCatching {
        events.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .map { doc ->
                DetectorEvent(
                    id = doc.id,
                    scores = (doc.get("scores") as? List<*>)
                        ?.mapNotNull { (it as? Number)?.toInt() }
                        .orEmpty(),
                    flaggedCount = (doc.getLong("flaggedCount") ?: 0L).toInt(),
                    wordCount = (doc.getLong("wordCount") ?: 0L).toInt(),
                    outcomeLooksRead = doc.getBoolean("outcomeLooksRead") ?: false,
                    sampleWord = doc.getString("sampleWord").orEmpty(),
                    sampleStrokesJson = doc.getString("sampleStrokesJson").orEmpty(),
                    appVersionCode = (doc.getLong("appVersionCode") ?: 0L).toInt(),
                    createdAtMillis = doc.getLong("createdAt") ?: 0L
                )
            }
    }

    private companion object {
        const val TAG = "DetectorEvents"

        /** One drawing's strokes, matching the report evidence cap. */
        const val MAX_SAMPLE_JSON = 60_000
    }
}
