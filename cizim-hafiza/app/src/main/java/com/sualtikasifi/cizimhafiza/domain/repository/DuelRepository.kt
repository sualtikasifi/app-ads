package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Duel
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import kotlinx.coroutines.flow.Flow

/** Asynchronous friend-vs-friend duels — see domain.model.Duel and firestore.rules' duels/{duelId}. */
interface DuelRepository {

    val currentUid: String?

    /** Turns a just-finished solo round (see GameViewModel's duel-challenge args) into a challenge for [opponentUid]. */
    suspend fun createDuel(
        opponentUid: String,
        opponentName: String,
        items: List<ResultItem>,
        challengerScore: Int,
        challengerCorrectCount: Int
    ): Result<Unit>

    /** One-shot fetch for the play screen — no live updates needed once the round is already loaded. */
    suspend fun getDuel(duelId: String): Result<Duel?>

    /** Duels waiting for this device to play, as the opponent. */
    fun observeIncomingDuels(): Flow<List<Duel>>

    /** Duels this device challenged someone else to, completed or not — for a "did they beat me" follow-up. */
    fun observeSentDuels(): Flow<List<Duel>>

    /** The opponent's guess-round outcome — completes the duel. Only ever called once per duel (see firestore.rules). */
    suspend fun submitDuelResult(duelId: String, opponentScore: Int, opponentCorrectCount: Int): Result<Unit>

    /** Clears the challenger's "new result" flag once they've opened a completed duel they sent. */
    suspend fun markSeenByChallenger(duelId: String): Result<Unit>

    /** Either participant may remove a duel from their own view once it no longer needs to be there. */
    suspend fun deleteDuel(duelId: String): Result<Unit>
}
