package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Penalty

/**
 * Applies the penalties a reviewer wrote against this account.
 *
 * Runs once when the app opens. A penalty is a record rather than a
 * command precisely so this can be true: a device offline for a week still
 * applies it on the day it comes back, and a device that already applied it
 * never applies it twice.
 */
interface PenaltyRepository {

    /**
     * Applies everything outstanding and returns what was applied, newest
     * first, so the player can be shown what happened and why.
     *
     * Empty on every ordinary launch — which is the common case, so this
     * must be cheap and must never block anything.
     */
    suspend fun applyOutstanding(): List<Penalty>

    /**
     * When this account may play the online modes again, or null if it may
     * play now. Read from the penalty record rather than computed here: a
     * lockout counted on the device's own clock could be sat out by moving
     * it forward.
     */
    suspend fun lockedUntilMillis(): Long?
}
