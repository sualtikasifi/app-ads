package com.sualtikasifi.cizimhafiza.domain.model

import java.time.LocalDate

/**
 * The calendar month a league table belongs to.
 *
 * The app already has XP, a friends list and public profile documents; what
 * it has never had is a reason to look at any of them again once the level
 * badge stops moving. A lifetime ranking cannot supply that — whoever
 * started first wins forever, and a new player can see at a glance that
 * catching up is hopeless. Resetting gives everyone the same empty table
 * again, which is the whole point: the contest is always winnable, and it
 * always expires.
 *
 * A month rather than a week, because the prize is a piece of artwork
 * stamped with its own month (see AvatarFrame's LEAGUE_CHAMPION frames).
 * Fifty-two of those a year is not a thing anybody is going to draw.
 *
 * The friends table and the global one (see [GlobalLeagueTable]) share this
 * arithmetic and the row type below. They differ in where the rows come
 * from: a friends table is built on the device from each friend's profile,
 * the global one is published whole by a scheduled function.
 */
object LeaguePeriod {

    /**
     * The month [date] falls in, as one comparable number.
     *
     * Months, unlike weeks, are not a fixed number of days, so this cannot
     * be arithmetic on an epoch day — it has to come off the calendar.
     *
     * The scheduled functions compute the same number the same way (see
     * functions/src/index.ts). They MUST agree: a profile is stamped with
     * the app's period id and the table is built by filtering on it.
     */
    fun periodIdFor(date: LocalDate): Long = date.year.toLong() * 12 + (date.monthValue - 1)

    /**
     * Days left in [date]'s month, for the "resets in N days" line. Zero on
     * the last day of the month, which the copy reads as "resets today".
     */
    fun daysRemainingIn(date: LocalDate): Int = date.lengthOfMonth() - date.dayOfMonth

    /** The id of the month before [periodId] — the one a closing hands prizes out for. */
    fun previous(periodId: Long): Long = periodId - 1

    /** `2026_09` for September 2026: the suffix the month's prize artwork is named with. */
    fun artworkSuffix(periodId: Long): String {
        val year = periodId / 12
        val month = periodId % 12 + 1
        return "%d_%02d".format(year, month)
    }
}

/**
 * One row of the table — a friend (or the player themselves) and what they
 * have earned since the first of the month.
 *
 * Denormalised on purpose: nickname, level and frame are copied onto the
 * public profile document alongside the score, so drawing the table is one
 * read per friend rather than a read plus a profile lookup each.
 */
data class LeagueEntry(
    val uid: String,
    val nickname: String,
    val periodXp: Int,
    val level: Int,
    val frameId: String,
    val isMe: Boolean,
    /**
     * A filler row in the global table rather than a person.
     *
     * The global table would otherwise be a handful of names on an empty
     * page, which reads as a broken feature rather than a young one. These
     * rows exist only in the published snapshot — no account is created for
     * them — and the UI must not offer to open, befriend or challenge one,
     * because there is nobody there.
     *
     * They also cannot win: see the podium rule in
     * functions/src/index.ts (buildGlobalLeaderboard), which keeps them
     * below every real player still holding a podium place.
     */
    val isBot: Boolean = false
)

/**
 * A whole week's table, already ranked.
 *
 * Ranking happens here rather than in the UI so the tie-break rule lives in
 * one testable place: equal scores are ordered by name, never by map
 * iteration order, which would otherwise shuffle two tied friends on every
 * recomposition.
 */
data class LeagueTable(
    val entries: List<LeagueEntry>,
    val daysRemaining: Int
) {
    /** The player's own 1-based position, or null if they are somehow not in the table. */
    val myRank: Int? get() = entries.indexOfFirst { it.isMe }.takeIf { it >= 0 }?.plus(1)

    companion object {
        fun rank(entries: List<LeagueEntry>, daysRemaining: Int): LeagueTable = LeagueTable(
            entries = entries.sortedWith(
                compareByDescending<LeagueEntry> { it.periodXp }
                    .thenBy { it.nickname.lowercase() }
                    .thenBy { it.uid }
            ),
            daysRemaining = daysRemaining
        )
    }
}

/**
 * The whole global table as the scheduled function published it.
 *
 * Read as ONE document, which is the entire reason the global table is
 * affordable: see buildGlobalLeaderboard in functions/src/index.ts.
 */
data class GlobalLeagueTable(
    val table: LeagueTable,
    val periodId: Long,
    /** When the function last rebuilt this — shown, because it is not live. */
    val generatedAtMillis: Long,
    /** What this month's top three win, or null if none is configured yet. */
    val rewardId: String?,
    val lastPeriod: LeaguePeriodResult?,
    /**
     * This device's own row in [lastPeriod], if it placed.
     *
     * Resolved where the signed-in uid is already known rather than handed
     * to the UI to work out, so the "you won" card has one thing to check
     * instead of a list to search on every recomposition.
     */
    val myLastPeriodWin: LeagueWinner?
)

/** the closed month the app is still handing prizes out for. */
data class LeaguePeriodResult(
    val periodId: Long,
    val rewardId: String?,
    val winners: List<LeagueWinner>
)

data class LeagueWinner(
    val uid: String,
    val nickname: String,
    val rank: Int,
    val periodXp: Int
)
