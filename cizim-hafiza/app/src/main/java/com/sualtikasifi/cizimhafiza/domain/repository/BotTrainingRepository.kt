package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import kotlinx.coroutines.flow.Flow

/**
 * Firestore-backed store for the bot opponent's hand-drawn training data
 * (see the "Bot Eğitim" main-menu entry). One entry per word id — the
 * strokes get replayed as the bot's own "drawing" in the persistent
 * shared room (see functions/src/index.ts's onBotRoomWrite), so this is
 * the same data whichever device opens the training screen.
 */
interface BotTrainingRepository {

    /** Every playable word, easiest first — the fixed order words are trained in. */
    suspend fun getAllWordsOrdered(): List<Word>

    /** Live set of word ids that already have training data. */
    fun observeTrainedWordIds(): Flow<Set<Int>>

    suspend fun saveTraining(word: Word, strokes: List<DrawingStroke>): Result<Unit>
}
