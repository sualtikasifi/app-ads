package com.sualtikasifi.cizimhafiza.domain.model

/**
 * A friends-only leaderboard that resets every Monday.
 *
 * The app already has XP, a friends list and public profile documents; what
 * it has never had is a reason to look at any of them again once the level
 * badge stops moving. A lifetime ranking cannot supply that — whoever
 * started first wins forever, and a new player can see at a glance that
 * catching up is hopeless. Resetting weekly gives everyone the same empty
 * table every Monday, which is the whole point: the contest is always
 * winnable, and it always expires.
 *
 * The friends table and the global one (see [GlobalLeagueTable]) share this
 * week arithmetic and the row type below. They differ in where the rows come
 * from: a friends table is built on the device from each friend's profile,
 * the global one is published whole by a scheduled function.
 */
object WeeklyLeague {

    /**
     * The Monday-aligned week an epoch day falls in.
     *
     * Epoch day 0 (1 January 1970) was a Thursday, so a naive `epochDay / 7`
     * would roll the table over mid-week. The +3 shifts the bucket boundary
     * onto Monday, which is what players expect a "week" to mean and what
     * the reset copy promises.
     */
    fun weekIdFor(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

    /** Days remaining in [weekIdFor]'s week, for the "resets in N days" line. */
    fun daysRemainingIn(epochDay: Long): Int {
        val nextWeekStart = (weekIdFor(epochDay) + 1) * 7 - 3
        return (nextWeekStart - epochDay).toInt().coerceAtLeast(0)
    }
}

/**
 * One row of the weekly table — a friend (or the player themselves) and what
 * they have earned since Monday.
 *
 * Denormalised on purpose: nickname, level and frame are copied onto the
 * public profile document alongside the score, so drawing the table is one
 * read per friend rather than a read plus a profile lookup each.
 */
data class LeagueEntry(
    val uid: String,
    val nickname: String,
    val weeklyXp: Int,
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
                compareByDescending<LeagueEntry> { it.weeklyXp }
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
    val weekId: Long,
    /** When the function last rebuilt this — shown, because it is not live. */
    val generatedAtMillis: Long,
    /** What this week's top three win, or null if none is configured yet. */
    val rewardId: String?,
    val lastWeek: LeagueWeekResult?,
    /**
     * This device's own row in [lastWeek], if it placed.
     *
     * Resolved where the signed-in uid is already known rather than handed
     * to the UI to work out, so the "you won" card has one thing to check
     * instead of a list to search on every recomposition.
     */
    val myLastWeekWin: LeagueWinner?
)

/** The closed week the app is still handing prizes out for. */
data class LeagueWeekResult(
    val weekId: Long,
    val rewardId: String?,
    val winners: List<LeagueWinner>
)

data class LeagueWinner(
    val uid: String,
    val nickname: String,
    val rank: Int,
    val weeklyXp: Int
)
