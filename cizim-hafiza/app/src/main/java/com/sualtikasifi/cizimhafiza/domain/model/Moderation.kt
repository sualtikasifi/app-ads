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
    /**
     * Which offence this was for the account, 1-based and lifetime — an
     * approved round does not clear it. Every third one carries a lockout.
     */
    val strike: Int,
    /**
     * When the account may play Hızlı Eşleş and online rooms again, or 0 for
     * no lockout. Set by the reviewer, not computed on the device: a phone
     * clock can be turned back.
     */
    val lockedUntilMillis: Long,
    val createdAtMillis: Long
)

/**
 * One page of runs, with what it takes to ask for the next one.
 *
 * The cursor is a `createdAt` rather than a document snapshot so it can live
 * in UI state and survive a rotation without carrying a Firestore type up
 * through the layers.
 */
data class RunPage(
    val runs: List<PendingRun>,
    /** Pass back as `after` to continue; null when nothing more was read. */
    val nextCursor: Long?,
    /** True once the collection has been read to the end. */
    val endReached: Boolean
)

/**
 * Who this device is as far as the moderation rules are concerned.
 *
 * Shown in the developer panel because the rules gate on a uid, and a uid is
 * invisible: when a write is refused there is otherwise no way to tell "the
 * rules are not published" from "this install is signed in as somebody else"
 * — and a reinstall can quietly produce the second.
 */
data class ReviewerIdentity(
    val uid: String?,
    val email: String?,
    val isReviewer: Boolean
)

object Moderation {

    /**
     * The one account the rules let moderate.
     *
     * MUST match the address in firestore.rules' reviewer() function. It is
     * duplicated here on purpose: the app cannot read the rules, so without a
     * copy it cannot tell the reviewer why a write was refused.
     */
    const val REVIEWER_EMAIL = "raunen3075@gmail.com"


    /**
     * Rejected rounds between lockouts.
     *
     * The count is lifetime and never resets — not even for an approved
     * round. Somebody who alternates a cheated round with an honest one
     * would otherwise sit permanently at one strike and never reach a
     * lockout at all, which is the obvious way to game a consecutive count.
     *
     * The lockout fires on every multiple instead: offences 3, 6, 9 and so
     * on each cost a day. Carrying the count past a lockout without this
     * would make every later offence an instant lockout.
     */
    const val STRIKES_BEFORE_LOCKOUT = 3

    const val LOCKOUT_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Rounds fetched in one page of the review queue.
     *
     * Three, because a page is not free: every row costs a second read for a
     * document holding ten drawings — around eighty kilobytes each. Loading
     * twenty-five at once pulled two megabytes off Firestore every time the
     * screen opened, most of it for rows nobody had scrolled to yet. The list
     * asks for the next three when the reviewer reaches the bottom.
     */
    const val REVIEW_PAGE_SIZE = 3
}
