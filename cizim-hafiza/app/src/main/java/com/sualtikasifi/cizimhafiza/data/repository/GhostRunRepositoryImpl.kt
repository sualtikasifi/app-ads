package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.GhostRun
import com.sualtikasifi.cizimhafiza.domain.model.GhostRunWord
import com.sualtikasifi.cizimhafiza.domain.model.GhostRuns
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.repository.GhostRunRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

/**
 * Firestore layout: two flat top-level collections sharing one id —
 * `ghostRuns/{runId}` (who played it, which words, what they scored) and
 * `ghostRunItems/{runId}` (that round's drawings).
 *
 * Flat and global on purpose. A recorded round belongs to everybody the
 * moment it lands — there is no graph to walk, no per-player subcollection
 * to fan out over, and no relationship between two players needed before one
 * can face the other. The whole pool is one query away, which is what makes
 * matching a database lookup rather than a matchmaking service.
 *
 * Split in two for one reason: size. The drawings are around a hundred
 * kilobytes and the rest of the round is around one, so a search that reads
 * three candidates to pick from would otherwise pull three hundred kilobytes
 * down a phone connection to show a name and a score. Kept apart, a search
 * costs a few kilobytes and the drawings are fetched once — at the end of a
 * match that was actually played. A sibling document rather than a
 * subcollection because Firestore does not cascade deletes: sharing the id
 * lets pruning and account deletion remove both with the id they already have.
 *
 * See domain.model.GhostRuns for the banding and shard scheme this writes,
 * and firestore.rules for the create/read enforcement these writes are
 * shaped to match key for key.
 */
class GhostRunRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : GhostRunRepository {

    private val ghostRuns get() = firestore.collection("ghostRuns")
    private val ghostRunItems get() = firestore.collection("ghostRunItems")
    private val json = Json { ignoreUnknownKeys = true }

    // The repository's own scope, not the caller's: this is started as the
    // result screen appears and the player may leave it immediately, which
    // would cancel a viewModelScope mid-upload. Same reasoning as
    // FriendRepositoryImpl's league fan-out.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun record(
        wordIds: List<Int>,
        mode: GameMode,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    ) {
        scope.launch {
            runCatching { write(wordIds, mode, perWord, items) }
                .onFailure { Log.w(TAG, "Ghost run not recorded", it) }
        }
    }

    private suspend fun write(
        wordIds: List<Int>,
        mode: GameMode,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    ) {
        val slice = GhostRuns.recordableSlice(wordIds, perWord, items) ?: return
        val uid = auth.currentUser?.uid
            ?: auth.signInAnonymously().await().user?.uid
            ?: return
        val level = PlayerLevel.levelForXp(settingsRepository.lifetimeXp.value)
        val runRef = ghostRuns.document()

        // One batch so a run can never exist without its drawings (or the
        // other way round): a half-written run would be offered as an
        // opponent and then have nothing to show at the end of the match.
        val batch = firestore.batch()
        batch.set(
            runRef,
            mapOf(
                "uid" to uid,
                "nickname" to settingsRepository.nicknameOrDefault,
                "level" to level.toLong(),
                "frameId" to AvatarFrame.resolve(settingsRepository.selectedAvatarFrameId.value, level).name,
                // The four fields the matching query filters on. levelBand is
                // an equality filter rather than a range because Firestore
                // allows only one inequality per query and shard needs it —
                // see GhostRuns for the whole reasoning.
                "levelBand" to GhostRuns.levelBandFor(level).toLong(),
                "shard" to Random.nextInt(GhostRuns.SHARD_COUNT).toLong(),
                // The two pools are not interchangeable: the English word
                // list deliberately drops 25 entries that only work in
                // Turkish, and a RELAXED round has no clock, so neither can
                // be compared against its counterpart.
                "language" to WordSeeder.currentLanguage(context),
                "mode" to mode.name,
                "wordCount" to slice.wordIds.size.toLong(),
                "wordIds" to slice.wordIds.map { it.toLong() },
                "totalScore" to slice.totalScore.toLong(),
                "correctCount" to slice.correctCount.toLong(),
                "fastestCorrectMs" to slice.fastestCorrectMs,
                "perWord" to slice.perWord.map {
                    mapOf(
                        "wordId" to it.wordId.toLong(),
                        "isCorrect" to it.isCorrect,
                        "responseTimeMs" to it.responseTimeMs,
                        "pointsAwarded" to it.pointsAwarded.toLong()
                    )
                },
                "createdAt" to System.currentTimeMillis()
            )
        )
        batch.set(
            ghostRunItems.document(runRef.id),
            // uid travels with the drawings too — it is what lets the rules
            // recognise this document's owner when pruning deletes it, since
            // a delete cannot read the sibling run to ask.
            mapOf("uid" to uid, "itemsJson" to json.encodeToString(slice.items))
        )
        batch.commit().await()

        pruneOwnRuns(uid)
    }

    override suspend fun findOpponent(level: Int): Result<GhostRun?> = runCatching {
        val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
        val language = WordSeeder.currentLanguage(context)
        val ownBand = GhostRuns.levelBandFor(level)

        // Own band first, then outwards a band at a time. Someone at level 3
        // would rather face a level 15 than see "nobody here yet", but they
        // should only face them once there is genuinely no one closer — which
        // is exactly what walking outwards gives, at one query per band and
        // no second composite index to maintain.
        for (band in bandsByDistanceFrom(ownBand)) {
            // A pivot per band, not per search: reusing one would keep
            // landing on the same corner of every band.
            val pivot = Random.nextInt(GhostRuns.SHARD_COUNT).toLong()
            val found = candidatesIn(language, band, pivot, above = true).firstOrNull { it.uid != uid }
            // Wrapping round to the bottom of the shard range matters most in
            // exactly the case that hurts: a nearly empty band, where a high
            // pivot would otherwise report the whole band as empty.
                ?: candidatesIn(language, band, pivot, above = false).firstOrNull { it.uid != uid }
            if (found != null) return@runCatching found
        }
        null
    }

    /**
     * Bands ordered by how far they are from [ownBand] — 4, 3, 5, 2, 6, …
     * Ties go to the lower band: facing someone slightly better is the more
     * interesting half of a mismatch.
     */
    private fun bandsByDistanceFrom(ownBand: Int): List<Int> {
        val maxBand = GhostRuns.levelBandFor(PlayerLevel.MAX_LEVEL)
        return (0..maxBand).sortedWith(compareBy({ abs(it - ownBand) }, { it }))
    }

    private suspend fun candidatesIn(
        language: String,
        band: Int,
        pivot: Long,
        above: Boolean
    ): List<GhostRun> {
        val shardFilter =
            if (above) ghostRuns.whereGreaterThanOrEqualTo("shard", pivot)
            else ghostRuns.whereLessThan("shard", pivot)
        val snapshot = shardFilter
            .whereEqualTo("language", language)
            .whereEqualTo("mode", GameMode.NORMAL.name)
            .whereEqualTo("wordCount", GhostRuns.RUN_WORD_COUNT.toLong())
            .whereEqualTo("levelBand", band.toLong())
            // More than one so a player whose own runs happen to sit next to
            // the pivot still gets an opponent without a second round trip.
            // Cheap now that the drawings are not in these documents.
            .orderBy("shard", if (above) Query.Direction.ASCENDING else Query.Direction.DESCENDING)
            .limit(CANDIDATES_PER_QUERY.toLong())
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toGhostRun() }
    }

    override suspend fun loadItems(runId: String): Result<List<ResultItem>> = runCatching {
        val raw = ghostRunItems.document(runId).get().await().getString("itemsJson")
            ?: return@runCatching emptyList()
        json.decodeFromString<List<ResultItem>>(raw)
    }

    /**
     * Null for any document the matching query should never have surfaced —
     * a run written by an older version, or one whose word list did not
     * survive. Skipping it costs one candidate; trusting it would crash the
     * search.
     */
    private fun DocumentSnapshot.toGhostRun(): GhostRun? {
        val uid = getString("uid") ?: return null
        // Runs written before the drawings moved to their own document still
        // carry them inline, and have no sibling for loadItems to find — so
        // a match against one would end on an empty opponent gallery. There
        // are only a handful and they age out; skipping them costs one
        // candidate and spares somebody a comparison with nothing to compare.
        if (contains("itemsJson")) return null
        val wordIds = (get("wordIds") as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?.takeIf { it.size == GhostRuns.RUN_WORD_COUNT }
            ?: return null
        return GhostRun(
            id = id,
            uid = uid,
            nickname = getString("nickname").orEmpty(),
            level = getLong("level")?.toInt() ?: 1,
            frameId = getString("frameId").orEmpty(),
            wordIds = wordIds,
            totalScore = getLong("totalScore")?.toInt() ?: 0,
            correctCount = getLong("correctCount")?.toInt() ?: 0,
            fastestCorrectMs = getLong("fastestCorrectMs")
        )
    }

    /**
     * Drops this player's oldest runs past [GhostRuns.MAX_RUNS_PER_PLAYER].
     *
     * Only runs occasionally. Pruning on every save would read a dozen
     * documents per finished game purely to delete one — for somebody
     * playing twenty rounds a day that is hundreds of billed reads a day, to
     * enforce a cap that nothing breaks if it is briefly exceeded. Firing
     * roughly once per [PRUNE_ODDS] saves keeps the overshoot to a handful of
     * documents and the cost to a tenth.
     */
    private suspend fun pruneOwnRuns(uid: String) {
        if (Random.nextInt(PRUNE_ODDS) != 0) return
        runCatching {
            val mine = ghostRuns
                .whereEqualTo("uid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                // Bounded so a runaway history can never be read in one go —
                // it converges over several prunes instead.
                .limit((GhostRuns.MAX_RUNS_PER_PLAYER + PRUNE_HEADROOM).toLong())
                .get()
                .await()
            val stale = mine.documents.drop(GhostRuns.MAX_RUNS_PER_PLAYER)
            if (stale.isEmpty()) return
            val batch = firestore.batch()
            stale.forEach {
                batch.delete(it.reference)
                // The sibling never outlives its run: an orphaned items
                // document would be invisible to every query and still be
                // billed for storage forever.
                batch.delete(ghostRunItems.document(it.id))
            }
            batch.commit().await()
        }.onFailure { Log.w(TAG, "Ghost run prune skipped", it) }
    }

    private companion object {
        const val TAG = "GhostRunRepository"
        const val PRUNE_ODDS = 10
        const val PRUNE_HEADROOM = 10
        const val CANDIDATES_PER_QUERY = 4
    }
}
