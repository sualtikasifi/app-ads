package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.domain.repository.XpEvent
import com.sualtikasifi.cizimhafiza.domain.repository.XpEventRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads/writes the single `config/xpEvent` document — see
 * [XpEventRepository] and firestore.rules' `config/xpEvent` match block
 * (reviewer-only write, same pattern as `leaderboards/config`).
 */
@Singleton
class XpEventRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : XpEventRepository {

    private val eventDoc get() = firestore.document("config/xpEvent")

    // Same cache-with-a-window shape as GlobalLeagueRepositoryImpl — this is
    // read once per match start, not once per word, so a few stale minutes
    // costs nothing and saves a read on every single round played.
    private var cached: XpEvent? = null
    private var cachedAtMillis = 0L

    override suspend fun currentMultiplier(): Int {
        val event = current().getOrNull() ?: return 1
        val stillRunning = event.active && System.currentTimeMillis() < event.endsAtMillis
        return if (stillRunning) event.multiplier else 1
    }

    override suspend fun current(): Result<XpEvent?> {
        val fresh = cached
        if (fresh != null && System.currentTimeMillis() - cachedAtMillis < XpEventRepository.REFRESH_WINDOW_MILLIS) {
            return Result.success(fresh)
        }
        return runCatching {
            val doc = eventDoc.get().await()
            val event = doc.data?.let { parse(it) }
            cached = event
            cachedAtMillis = System.currentTimeMillis()
            event
        }
    }

    override suspend fun startEvent(multiplier: Int, durationMillis: Long, label: String?): Result<Unit> = runCatching {
        eventDoc.set(
            mapOf(
                "active" to true,
                "multiplier" to multiplier,
                "endsAtMillis" to (System.currentTimeMillis() + durationMillis),
                "label" to label
            )
        ).await()
        cached = null
        Unit
    }

    override suspend fun stopEvent(): Result<Unit> = runCatching {
        eventDoc.set(mapOf("active" to false)).await()
        cached = null
        Unit
    }

    private fun parse(data: Map<String, Any?>): XpEvent = XpEvent(
        active = data["active"] as? Boolean ?: false,
        multiplier = (data["multiplier"] as? Number)?.toInt() ?: 1,
        endsAtMillis = (data["endsAtMillis"] as? Number)?.toLong() ?: 0L,
        label = data["label"] as? String
    )
}
