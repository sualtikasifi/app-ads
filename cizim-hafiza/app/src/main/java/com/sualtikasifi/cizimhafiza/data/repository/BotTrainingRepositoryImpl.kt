package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.data.local.dao.WordDao
import com.sualtikasifi.cizimhafiza.data.local.entity.toDomain
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.BotTrainingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
        val registration = trainedWords.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val ids = snapshot?.documents?.mapNotNull { it.id.toIntOrNull() }?.toSet() ?: emptySet()
            trySend(ids)
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
    }
}
