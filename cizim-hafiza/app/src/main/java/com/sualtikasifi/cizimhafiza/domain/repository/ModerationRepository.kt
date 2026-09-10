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
     * Lets a round into the live pool.
     *
     * Also clears the author's consecutive-strike count: the point of the
     * count is "is this account cheating right now", and an approved round
     * answers no.
     */
    suspend fun approve(runId: String): Result<Unit>

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
