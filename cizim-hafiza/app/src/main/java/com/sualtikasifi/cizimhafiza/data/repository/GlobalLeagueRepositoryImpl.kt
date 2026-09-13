package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.GlobalLeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.LeagueEntry
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.LeaguePeriodResult
import com.sualtikasifi.cizimhafiza.domain.model.LeagueWinner
import com.sualtikasifi.cizimhafiza.domain.repository.GlobalLeagueRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the one document the scheduled function publishes — see
 * [GlobalLeagueRepository] and functions/src/index.ts.
 */
@Singleton
class GlobalLeagueRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : GlobalLeagueRepository {

    private val snapshotDoc get() = firestore.document("leaderboards/global")
    private val configDoc get() = firestore.document("leaderboards/config")

    // The whole point of publishing one document is that reading it is
    // cheap; re-reading it on every tab switch would give that back.
    private var cached: GlobalLeagueTable? = null
    private var cachedAtMillis = 0L

    override suspend fun table(forceRefresh: Boolean): Result<GlobalLeagueTable> {
        val fresh = cached
        if (!forceRefresh && fresh != null &&
            System.currentTimeMillis() - cachedAtMillis < GlobalLeagueRepository.REFRESH_WINDOW_MILLIS
        ) {
            return Result.success(fresh)
        }
        return runCatching {
            val doc = snapshotDoc.get().await()
            // An absent document is not an error: it is what the app sees
            // before the scheduled function has ever run, and the screen has
            // an empty state for exactly that.
            val table = parse(doc.data.orEmpty())
            cached = table
            cachedAtMillis = System.currentTimeMillis()
            table
        }
    }

    override suspend fun setWeekReward(rewardId: String?): Result<Unit> = runCatching {
        configDoc.set(mapOf("rewardId" to rewardId), SetOptions.merge()).await()
        // The published snapshot still carries the OLD reward until the next
        // rebuild, so drop the cache rather than letting the panel show a
        // change it just made as not having happened.
        cached = null
        Unit
    }

    private fun parse(data: Map<String, Any?>): GlobalLeagueTable {
        val myUid = auth.currentUser?.uid
        val rows = (data["entries"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

        val entries = rows.mapIndexed { index, row ->
            val uid = row["uid"] as? String
            val level = (row["level"] as? Number)?.toInt() ?: 1
            LeagueEntry(
                // A filler row has no account, so it has no uid. The list
                // still needs a stable key per row, and its position in the
                // published (already ranked) list is exactly that.
                uid = uid ?: "$BOT_KEY_PREFIX$index",
                nickname = (row["nickname"] as? String)?.takeIf { it.isNotBlank() } ?: "?",
                periodXp = (row["periodXp"] as? Number)?.toInt() ?: 0,
                level = level,
                // The function does not send a frame: doing so would mean
                // duplicating the whole frame ladder in TypeScript, where it
                // would drift the first time a frame was added here.
                frameId = AvatarFrame.highestUnlockedFor(level).name,
                isMe = uid != null && uid == myUid,
                isBot = uid == null
            )
        }

        val daysRemaining = (data["daysRemaining"] as? Number)?.toInt() ?: 0
        val lastPeriod = parseLastPeriod(data["lastPeriod"] as? Map<*, *>)
        return GlobalLeagueTable(
            // Re-ranked here rather than trusted as ordered: the tie-break
            // then matches the friends table exactly, and myRank comes free.
            table = LeagueTable.rank(entries, daysRemaining),
            periodId = (data["periodId"] as? Number)?.toLong() ?: 0L,
            generatedAtMillis = (data["generatedAt"] as? Number)?.toLong() ?: 0L,
            rewardId = data["rewardId"] as? String,
            lastPeriod = lastPeriod,
            myLastPeriodWin = lastPeriod?.winners?.firstOrNull { it.uid == myUid }
        )
    }

    private fun parseLastPeriod(data: Map<*, *>?): LeaguePeriodResult? {
        if (data == null) return null
        val winners = (data["winners"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { row ->
                val uid = row["uid"] as? String ?: return@mapNotNull null
                LeagueWinner(
                    uid = uid,
                    nickname = (row["nickname"] as? String)?.takeIf { it.isNotBlank() } ?: "?",
                    rank = (row["rank"] as? Number)?.toInt() ?: 0,
                    periodXp = (row["periodXp"] as? Number)?.toInt() ?: 0
                )
            }
        return LeaguePeriodResult(
            periodId = (data["periodId"] as? Number)?.toLong() ?: 0L,
            rewardId = data["rewardId"] as? String,
            winners = winners
        )
    }

    private companion object {
        const val BOT_KEY_PREFIX = "filler:"
    }
}
