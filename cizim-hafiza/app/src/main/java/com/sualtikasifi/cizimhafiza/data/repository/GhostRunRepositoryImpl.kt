package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sualtikasifi.cizimhafiza.data.local.WordSeeder
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

/**
 * Firestore layout: one flat top-level `ghostRuns/{runId}` collection.
 *
 * Flat and global on purpose. A recorded round belongs to everybody the
 * moment it lands — there is no graph to walk, no per-player subcollection
 * to fan out over, and no relationship between two players needed before one
 * can face the other. The whole pool is one query away, which is what makes
 * matching a database lookup rather than a matchmaking service.
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
    private val json = Json { ignoreUnknownKeys = true }

    // The repository's own scope, not the caller's: this is started as the
    // result screen appears and the player may leave it immediately, which
    // would cancel a viewModelScope mid-upload. Same reasoning as
    // FriendRepositoryImpl's league fan-out.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun record(
        wordIds: List<Int>,
        mode: GameMode,
        totalScore: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    ) {
        scope.launch {
            runCatching { write(wordIds, mode, totalScore, correctCount, fastestCorrectMs, perWord, items) }
                .onFailure { Log.w(TAG, "Ghost run not recorded", it) }
        }
    }

    private suspend fun write(
        wordIds: List<Int>,
        mode: GameMode,
        totalScore: Int,
        correctCount: Int,
        fastestCorrectMs: Long?,
        perWord: List<GhostRunWord>,
        items: List<ResultItem>
    ) {
        val uid = auth.currentUser?.uid
            ?: auth.signInAnonymously().await().user?.uid
            ?: return
        val level = PlayerLevel.levelForXp(settingsRepository.lifetimeXp.value)

        ghostRuns.add(
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
                "wordCount" to wordIds.size.toLong(),
                "wordIds" to wordIds.map { it.toLong() },
                "totalScore" to totalScore.toLong(),
                "correctCount" to correctCount.toLong(),
                "fastestCorrectMs" to fastestCorrectMs,
                // Kept apart from itemsJson so a future cost squeeze can drop
                // the drawings and still leave a playable opponent: the match
                // is decided on these numbers, the drawings are what make it
                // worth looking at.
                "perWord" to perWord.map {
                    mapOf(
                        "wordId" to it.wordId.toLong(),
                        "isCorrect" to it.isCorrect,
                        "responseTimeMs" to it.responseTimeMs,
                        "pointsAwarded" to it.pointsAwarded.toLong()
                    )
                },
                "itemsJson" to json.encodeToString(items),
                "createdAt" to System.currentTimeMillis()
            )
        ).await()

        pruneOwnRuns(uid)
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
            stale.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }.onFailure { Log.w(TAG, "Ghost run prune skipped", it) }
    }

    private companion object {
        const val TAG = "GhostRunRepository"
        const val PRUNE_ODDS = 10
        const val PRUNE_HEADROOM = 10
    }
}
