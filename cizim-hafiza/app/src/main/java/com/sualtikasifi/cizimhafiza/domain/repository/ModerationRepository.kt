package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.PendingRun

/**
 * The reviewer's side of the Hızlı Eşleş pool: what is waiting, and the two
 * decisions that can be taken about it.
 *
 * Only ever called from the passcode-gated review screen. Nothing here is
 * reachable by a player.
 */
interface ModerationRepository {

    /** Oldest first — the queue is worked through in the order it arrived. */
    suspend fun pendingRuns(limit: Int): Result<List<PendingRun>>

    /**
     * What is already in the live pool, newest first.
     *
     * Read back in the same shape as the queue so the same row can show it:
     * a run in the pool is a run that was approved, and the only thing worth
     * doing with one is looking at it again.
     */
    suspend fun poolRuns(limit: Int): Result<List<PendingRun>>

    /** Lets a round into the live pool. */
    suspend fun approve(runId: String): Result<Unit>

    /**
     * Pulls a round back out of the pool and into the queue.
     *
     * The undo for [approve], and the way rounds that reached the pool before
     * review existed get looked at. No penalty: this only says the round is
     * not playable until somebody has judged it.
     */
    suspend fun sendBackToQueue(runId: String): Result<Unit>

    /**
     * Rejects a round and penalises its author.
     *
     * Deletes the round, writes a [com.sualtikasifi.cizimhafiza.domain.model.Penalty]
     * for the XP it paid out, and — on the third consecutive rejection —
     * locks the account out of the online modes for a day.
     *
     * The lockout deadline is computed here, on the reviewer's clock, and
     * stored as an absolute time. A device could otherwise sit out its
     * lockout by moving its own clock forward.
     */
    suspend fun reject(runId: String, xpToRevoke: Int): Result<Unit>
}
