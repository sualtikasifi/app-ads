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
 * Deliberately scoped to friends rather than global. A global table in a
 * small player base is either empty or dominated by strangers, and neither
 * is worth opening twice.
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
    val isMe: Boolean
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
