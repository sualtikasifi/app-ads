package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.GlobalLeagueTable

/**
 * The global weekly table, as published by the scheduled
 * buildGlobalLeaderboard function (functions/src/index.ts).
 *
 * Read-only from the app's side by design: a table every device could write
 * to is a table every device could win.
 */
interface GlobalLeagueRepository {

    /**
     * The published table. One Firestore read, cached in memory for
     * [REFRESH_WINDOW_MILLIS] — the document only changes once an hour,
     * so a player toggling between the two league tabs should not pay for it
     * each time.
     */
    suspend fun table(forceRefresh: Boolean = false): Result<GlobalLeagueTable>

    /**
     * Sets the cosmetic this week's top three win. Reviewer-only — the
     * Firestore rules on leaderboards/config enforce it, this is just the
     * call the review panel makes.
     */
    suspend fun setWeekReward(rewardId: String?): Result<Unit>

    companion object {
        /**
         * Shorter than the hourly rebuild on purpose: the point is to
         * collapse a burst of opens in one sitting, not to hold a stale
         * table for most of an hour after a rebuild landed.
         */
        const val REFRESH_WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
