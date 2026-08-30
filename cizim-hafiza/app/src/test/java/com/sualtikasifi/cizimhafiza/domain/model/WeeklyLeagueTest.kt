package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyLeagueTest {

    /**
     * The table is promised to reset "every Monday". Epoch day 0 was a
     * Thursday, so a naive epochDay/7 would roll it over mid-week — this
     * pins the boundary to the day the copy actually claims.
     */
    @Test
    fun `a week starts on Monday`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val isFirstDayOfBucket =
                WeeklyLeague.weekIdFor(date.toEpochDay()) != WeeklyLeague.weekIdFor(date.minusDays(1).toEpochDay())
            if (isFirstDayOfBucket) {
                assertEquals("$date started a new week but is not a Monday", DayOfWeek.MONDAY, date.dayOfWeek)
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `every day of one week shares a week id`() {
        val monday = LocalDate.of(2026, 8, 31) // a Monday
        val ids = (0..6).map { WeeklyLeague.weekIdFor(monday.plusDays(it.toLong()).toEpochDay()) }
        assertEquals(1, ids.toSet().size)
        assertNotEquals(ids.first(), WeeklyLeague.weekIdFor(monday.plusDays(7).toEpochDay()))
    }

    @Test
    fun `days remaining counts down to the next Monday`() {
        val monday = LocalDate.of(2026, 8, 31)
        assertEquals(7, WeeklyLeague.daysRemainingIn(monday.toEpochDay()))
        assertEquals(1, WeeklyLeague.daysRemainingIn(monday.plusDays(6).toEpochDay()))
        assertEquals(7, WeeklyLeague.daysRemainingIn(monday.plusDays(7).toEpochDay()))
    }

    @Test
    fun `a pre-1970 clock does not break the week maths`() {
        // floorDiv, not /, so a badly-set device clock cannot land two
        // adjacent days in wildly different buckets.
        listOf(-1L, -8L, -365L).forEach { day ->
            assertTrue(WeeklyLeague.daysRemainingIn(day) in 1..7)
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
