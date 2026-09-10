package com.sualtikasifi.cizimhafiza.domain.model

/**
 * One finished round waiting to be let into the Hızlı Eşleş pool, as the
 * review screen reads it back.
 *
 * Every round goes through here. The automatic check (see
 * [WrittenWordDetector]) was measured against real handwriting and does not
 * work, so the only thing that reliably separates a drawn round from a
 * written one is somebody looking at it — and the pool is small enough that
 * looking is still cheap.
 */
data class PendingRun(
    val id: String,
    val uid: String,
    val nickname: String,
    val level: Int,
    /** All ten drawings, in the order they were played. */
    val items: List<ResultItem>,
    val totalScore: Int,
    val correctCount: Int,
    /** What the round paid its author — exactly what a rejection takes back. */
    val xpEarned: Int,
    val createdAtMillis: Long
)

/**
 * A penalty applied to one account for one rejected round.
 *
 * Written by the reviewer, applied by the offending device the next time it
 * opens the app — see PenaltyRepository. It is deliberately a record of a
 * decision rather than a command that fires once: a device that is offline
 * for a week still applies it when it comes back, and a device that already
 * applied it never applies it twice.
 */
data class Penalty(
    val id: String,
    val uid: String,
    /** XP taken back — what the rejected round paid out. */
    val xpRevoked: Int,
    /** Which offence this was for the account, 1-based. Three means a lockout. */
    val strike: Int,
    /**
     * When the account may play Hızlı Eşleş and online rooms again, or 0 for
     * no lockout. Set by the reviewer, not computed on the device: a phone
     * clock can be turned back.
     */
    val lockedUntilMillis: Long,
    val createdAtMillis: Long
)

object Moderation {

    /**
     * Consecutive rejected rounds before the account loses the online modes
     * for a day.
     *
     * Consecutive, not lifetime: one approved round clears the count. Somebody
     * who cheated once a year ago and has played honestly since is not one
     * strike away from a lockout.
     */
    const val STRIKES_BEFORE_LOCKOUT = 3

    const val LOCKOUT_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Rounds shown in one page of the review queue.
     *
     * Small on purpose — each row carries ten drawings, and the point is to
     * clear them in a sitting rather than to scroll a backlog.
     */
    const val REVIEW_PAGE_SIZE = 25
}
