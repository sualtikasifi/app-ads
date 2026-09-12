package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeaguePeriodTest {

    /**
     * The period is a calendar month, so its boundary has to come off the
     * calendar — month lengths vary and no arithmetic on an epoch day gets
     * them right.
     */
    @Test
    fun `a period starts on the first of the month`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(800) {
            val startsBucket =
                LeaguePeriod.periodIdFor(date) != LeaguePeriod.periodIdFor(date.minusDays(1))
            if (startsBucket) {
                assertEquals("$date started a period but is not the 1st", 1, date.dayOfMonth)
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `every day of one month shares a period id`() {
        val first = LocalDate.of(2026, 2, 1) // a 28-day month, the awkward one
        val ids = (0 until first.lengthOfMonth()).map { LeaguePeriod.periodIdFor(first.plusDays(it.toLong())) }
        assertEquals(1, ids.toSet().size)
        assertNotEquals(ids.first(), LeaguePeriod.periodIdFor(first.plusMonths(1)))
    }

    @Test
    fun `period ids run consecutively across a year boundary`() {
        val december = LeaguePeriod.periodIdFor(LocalDate.of(2026, 12, 5))
        val january = LeaguePeriod.periodIdFor(LocalDate.of(2027, 1, 5))
        assertEquals(december + 1, january)
        assertEquals(december, LeaguePeriod.previous(january))
    }

    @Test
    fun `days remaining counts down to the end of the month`() {
        assertEquals(30, LeaguePeriod.daysRemainingIn(LocalDate.of(2026, 3, 1)))
        assertEquals(0, LeaguePeriod.daysRemainingIn(LocalDate.of(2026, 3, 31)))
        // February, where a fixed seven-day assumption would have been wrong.
        assertEquals(0, LeaguePeriod.daysRemainingIn(LocalDate.of(2026, 2, 28)))
    }

    /**
     * The suffix names the month's prize artwork, and the scheduled function
     * builds the same string independently (rewardIdFor in
     * functions/src/index.ts). A drift here hands winners the wrong frame, or
     * none at all.
     */
    @Test
    fun `artwork suffix is a zero-padded year and month`() {
        assertEquals("2026_09", LeaguePeriod.artworkSuffix(LeaguePeriod.periodIdFor(LocalDate.of(2026, 9, 12))))
        assertEquals("2026_12", LeaguePeriod.artworkSuffix(LeaguePeriod.periodIdFor(LocalDate.of(2026, 12, 31))))
        assertEquals("2027_01", LeaguePeriod.artworkSuffix(LeaguePeriod.periodIdFor(LocalDate.of(2027, 1, 1))))
    }

    @Test
    fun `every shipped league frame is the prize for a real month`() {
        AvatarFrame.entries.filter { it.isLeagueReward }.forEach { frame ->
            val reward = LeagueReward.Frame(frame)
            val label = reward.periodLabel
            assertTrue("${frame.name} has no month in its name", label != null)
            val (year, month) = label!!.split("-").map { it.toInt() }
            val periodId = LeaguePeriod.periodIdFor(LocalDate.of(year, month, 1))
            assertEquals("${frame.name} is not the prize its own month resolves to",
                reward, LeagueReward.forPeriod(periodId))
        }
    }

    // --- ranking ---

    private fun entry(uid: String, nickname: String, xp: Int, isMe: Boolean = false) =
        LeagueEntry(uid, nickname, xp, level = 1, frameId = AvatarFrame.DEFAULT.name, isMe = isMe)

    @Test
    fun `highest weekly XP ranks first`() {
        val table = LeagueTable.rank(
            listOf(entry("a", "Ali", 10), entry("b", "Bora", 90), entry("c", "Ceren", 50)),
            daysRemaining = 3
        )
        assertEquals(listOf("Bora", "Ceren", "Ali"), table.entries.map { it.nickname })
    }

    /**
     * Ties must not depend on map iteration order — two friends level on
     * score would otherwise swap places on every recomposition, which reads
     * as the table flickering.
     */
    @Test
    fun `ties break by name then uid, deterministically`() {
        val rows = listOf(entry("z", "Zeynep", 40), entry("a", "Ahmet", 40), entry("m", "ahmet", 40))
        val first = LeagueTable.rank(rows, 3).entries.map { it.uid }
        val second = LeagueTable.rank(rows.reversed(), 3).entries.map { it.uid }
        assertEquals(first, second)
        // Case-insensitive, so "ahmet" and "Ahmet" sort together rather than
        // splitting around Zeynep on ASCII case ordering.
        assertEquals(listOf("a", "m", "z"), first)
    }

    @Test
    fun `myRank is the player's own one-based position`() {
        val table = LeagueTable.rank(
            listOf(entry("a", "Ali", 10), entry("me", "Ben", 50, isMe = true), entry("c", "Ceren", 90)),
            daysRemaining = 2
        )
        assertEquals(2, table.myRank)
    }

    @Test
    fun `myRank is null when the player is absent`() {
        assertEquals(null, LeagueTable.rank(listOf(entry("a", "Ali", 10)), 2).myRank)
        assertEquals(null, LeagueTable.rank(emptyList(), 2).myRank)
    }

    @Test
    fun `a zero-score week still lists everyone`() {
        // Friends who have not played yet this week must appear at zero, not
        // vanish — an almost-empty table on a Monday morning reads as broken.
        val table = LeagueTable.rank(
            listOf(entry("a", "Ali", 0), entry("me", "Ben", 0, isMe = true)),
            daysRemaining = 7
        )
        assertEquals(2, table.entries.size)
        // Everyone on zero, so the alphabetical tie-break decides: Ali ahead
        // of Ben. Position is stable rather than arbitrary, which is the
        // property that matters on a Monday when every score is still 0.
        assertEquals(2, table.myRank)
    }
}
