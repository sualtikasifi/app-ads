package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.BotTrainingRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Firestore layout: botTrainedWords/{wordId} — one doc per trained word,
 * doc id is the word's Room id as a string. See firestore.rules for the
 * (deliberately open, any-authenticated-user) access rule.
 */
class BotTrainingRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : BotTrainingRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val trainedWords get() = firestore.collection("botTrainedWords")

    /**
     * Mirror of [trainedWords]' doc ids in a single small doc. Reading the
     * collection itself just to learn which words are done means pulling
     * every stored drawing with it (strokesJson runs to several KB a piece,
     * so the whole collection is megabytes) — and since the screen now
     * waits for server-confirmed data before showing a word, that download
     * sits directly in front of the trainer. Written in the same batch as
     * the drawing (see [saveTraining]) so it cannot drift.
     */
    private val trainedIndexDoc get() = firestore.collection("botTrainingIndex").document("trained")

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    override suspend fun getAllWordsOrdered(): List<Word> =
        wordDao.getAllWordsOrderedByDifficulty().map { it.toDomain() }

    override suspend fun resetConnection() {
        firestore.disableNetwork().await()
        firestore.enableNetwork().await()
    }

    override fun observeTrainedWordIds(): Flow<Set<Int>> = callbackFlow {
        // Anonymous sign-in is fired (not awaited) from CizimHafizaApp at
        // process start, so a listener attached before it lands would get
        // PERMISSION_DENIED from firestore.rules' `request.auth != null`
        // check — awaited here so this never races it.
        ensureSignedIn()
        // Firestore fires the listener immediately with whatever is in the
        // on-device cache, before it has talked to the server at all. That
        // first cached set is stale by definition — it's missing every word
        // trained since this device last synced (including everything
        // trained from another phone). Acting on it puts an
        // already-trained word back in front of the trainer, who then draws
        // it a second time and silently overwrites the original (saveTraining
        // uses set()). So the first snapshot is only trusted once it
        // actually came from the server; after that, cached snapshots are
        // exactly what we want — they carry this device's own pending
        // writes, which is what advances the screen to the next word
        // immediately after a save.
        var sawServerSnapshot = false
        var registration: ListenerRegistration? = null

        // Falls back to reading the collection itself whenever the index
        // can't be trusted. Treating a missing or partial index as "nothing
        // trained" would hand the trainer words that are already done and
        // let saveTraining's set() overwrite the originals, so this path
        // still prefers the full collection over guessing "nothing trained".
        //
        // Unlike the index listener above, this one does NOT wait for a
        // server-confirmed snapshot before trusting it: this path is already
        // the slow, degraded one (only reached when the fast index is
        // missing/incomplete/unreadable), pulling the whole collection —
        // several MB of strokesJson — instead of one small doc, so on a poor
        // connection waiting for a real round-trip on top of that download
        // can blow well past SERVER_SYNC_TIMEOUT_MS and leave the trainer
        // stuck on "sunucudan alınamadı" with nothing to draw at all. The
        // worst a stale cached snapshot can do here is offer one word that
        // turns out to already be trained — the trainer draws it again and
        // saveTraining's set() harmlessly overwrites the original — which is
        // a far smaller cost than the screen not working.
        fun listenToCollection() {
            registration = trainedWords.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Not close(): this is already the last-resort path, so
                    // failing it outright leaves the screen with nothing at
                    // all. Firestore retries its own listener internally for
                    // transient faults; anything it cannot recover from is
                    // reported through the timeout in BotTrainingViewModel.
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.id.toIntOrNull() }.toSet())
            }
        }

        registration = trainedIndexDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Same fallback as an incomplete index below, not a fatal
                // close(): botTrainingIndex/trained needs its own Firestore
                // rule (see firestore.rules), and if the console's deployed
                // rules ever drift out of sync with that file — e.g. a rules
                // change that added this doc's read permission never got
                // published — every trainer would hit a permanent
                // PERMISSION_DENIED here and see "Kelimeler yüklenemedi" with
                // no way to recover. The full-collection listener has its own
                // (broader) rule and keeps working either way.
                registration?.remove()
                listenToCollection()
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (!sawServerSnapshot) {
                if (snapshot.metadata.isFromCache) return@addSnapshotListener
                // The index only covers everything once it has been
                // backfilled from the existing collection (see
                // BOT_TRAINING_INDEX.md). Until then it holds nothing, or
                // just the handful of words saved since this code shipped —
                // and trusting that partial list would present hundreds of
                // already-drawn words as untrained.
                if (snapshot.getBoolean(FIELD_COMPLETE) != true) {
                    registration?.remove()
                    listenToCollection()
                    return@addSnapshotListener
                }
                sawServerSnapshot = true
            }
            val ids = (snapshot.get(FIELD_WORD_IDS) as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Number)?.toInt() }
                .toSet()
            trySend(ids)
        }
        awaitClose { registration?.remove() }
    }

    override suspend fun saveTraining(word: Word, strokes: List<DrawingStroke>): Result<Unit> = runCatching {
        ensureSignedIn()
        val strokesJson = json.encodeToString(strokes.simplified())
        // firestore.rules rejects a botTrainedWords write whose strokesJson
        // is too large with a bare PERMISSION_DENIED — indistinguishable
        // from any other rule failure once it reaches the client — so this
        // is checked here first to give the trainer an actionable message
        // instead of the generic "Kaydedilemedi" that used to fire on every
        // save. A careful, untimed training drawing routinely ran well past
        // the old 200,000-char cap before strokes.simplified() below existed.
        check(strokesJson.length < MAX_STROKES_JSON_LENGTH) { DRAWING_TOO_LARGE_MESSAGE }
        // One batch, so the drawing and the index entry land together or not
        // at all — an index that had drifted from the collection would
        // either hide a word that still needs drawing or re-offer one that
        // is already done. arrayUnion (rather than rewriting the array)
        // keeps two people training from two phones from clobbering each
        // other's additions.
        firestore.batch().apply {
            set(
                trainedWords.document(word.id.toString()),
                mapOf(
                    "wordId" to word.id,
                    "word" to word.text,
                    "category" to word.category,
                    "difficulty" to word.difficulty.name,
                    "strokesJson" to strokesJson,
                    "trainedAt" to System.currentTimeMillis()
                )
            )
            set(
                trainedIndexDoc,
                mapOf(FIELD_WORD_IDS to FieldValue.arrayUnion(word.id)),
                SetOptions.merge()
            )
        }.commit().await()
        // set().await() only confirms the write landed in Firestore's local
        // offline cache, not that the server has it — with a weak/lost
        // connection right at that moment, this would otherwise report
        // "saved" while the doc never actually reaches the cloud, and the
        // word would silently keep reappearing as untrained later (this is
        // exactly what happened to a real trained word before this fix).
        // Waiting for the queued write to actually flush to the server
        // turns that into a real, user-visible "Kaydedilemedi" instead.
        try {
            withTimeout(20_000) { firestore.waitForPendingWrites().await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("İnternet bağlantısı zayıf, kelime sunucuya kaydedilemedi", e)
        }
    }

    /**
     * Drops touch points that added no visible detail. A slow, careful drag
     * — which bot-training drawings tend to be, since the screen is
     * untimed — samples far more points than the shape needs; that bloat is
     * exactly what was pushing strokesJson past firestore.rules' size cap
     * and making saves fail with "Kaydedilemedi" regardless of connection
     * quality. Keeping only points that moved at least
     * [MIN_POINT_DISTANCE_PX] from the last kept point cuts a typical
     * training drawing's encoded size by 70-90% with no visible change to
     * the shape the bot replays.
     */
    private fun List<DrawingStroke>.simplified(): List<DrawingStroke> = map { stroke ->
        if (stroke.size <= 2) return@map stroke
        val kept = mutableListOf(stroke.first())
        for (point in stroke) {
            val last = kept.last()
            val dx = point.x - last.x
            val dy = point.y - last.y
            if (dx * dx + dy * dy >= MIN_POINT_DISTANCE_PX * MIN_POINT_DISTANCE_PX) {
                kept.add(point)
            }
        }
        if (kept.last() != stroke.last()) kept.add(stroke.last())
        kept
    }

    private companion object {
        const val FIELD_WORD_IDS = "wordIds"

        /**
         * Set once, by the one-off backfill — never by [saveTraining], whose
         * arrayUnion merge would otherwise mark a one-entry index complete.
         */
        const val FIELD_COMPLETE = "complete"

        const val MIN_POINT_DISTANCE_PX = 2f

        /** Headroom under firestore.rules' botTrainedWords strokesJson cap (500,000) — see [saveTraining]. */
        const val MAX_STROKES_JSON_LENGTH = 480_000

        const val DRAWING_TOO_LARGE_MESSAGE = "drawing-too-large"
    }
}
