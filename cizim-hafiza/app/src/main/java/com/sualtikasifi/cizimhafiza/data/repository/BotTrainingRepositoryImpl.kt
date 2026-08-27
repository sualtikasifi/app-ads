package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    override suspend fun getAllWordsOrdered(): List<Word> =
        wordDao.getAllWordsOrderedByDifficulty().map { it.toDomain() }

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
        val registration = trainedWords.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (!sawServerSnapshot) {
                if (snapshot.metadata.isFromCache) return@addSnapshotListener
                sawServerSnapshot = true
            }
            trySend(snapshot.documents.mapNotNull { it.id.toIntOrNull() }.toSet())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveTraining(word: Word, strokes: List<DrawingStroke>): Result<Unit> = runCatching {
        ensureSignedIn()
        trainedWords.document(word.id.toString()).set(
            mapOf(
                "wordId" to word.id,
                "word" to word.text,
                "category" to word.category,
                "difficulty" to word.difficulty.name,
                "strokesJson" to json.encodeToString(strokes),
                "trainedAt" to System.currentTimeMillis()
            )
        ).await()
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
}
