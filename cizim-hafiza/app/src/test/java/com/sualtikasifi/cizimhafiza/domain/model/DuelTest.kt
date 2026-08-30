package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuelTest {

    private fun duel(challengerScore: Int, opponentScore: Int?) = Duel(
        id = "d1",
        challengerUid = "challenger",
        challengerName = "Ada",
        opponentUid = "opponent",
        opponentName = "Deniz",
        items = emptyList(),
        challengerScore = challengerScore,
        challengerCorrectCount = 0,
        opponentScore = opponentScore,
        opponentCorrectCount = opponentScore?.let { 0 }
    )

    @Test
    fun `challengerWon is null while the opponent has not played yet`() {
        assertNull(duel(challengerScore = 50, opponentScore = null).challengerWon)
    }

    @Test
    fun `challengerWon is true when the challenger scored higher`() {
        assertEquals(true, duel(challengerScore = 50, opponentScore = 30).challengerWon)
    }

    @Test
    fun `challengerWon is false when the opponent scored higher`() {
        assertEquals(false, duel(challengerScore = 30, opponentScore = 50).challengerWon)
    }

    @Test
    fun `challengerWon is null on an exact tie`() {
        assertNull(duel(challengerScore = 40, opponentScore = 40).challengerWon)
    }

    @Test
    fun `totalWords reflects the recorded item count`() {
        val withWords = duel(challengerScore = 0, opponentScore = null).copy(
            items = listOf(
                ResultItem("kedi", true, emptyList()),
                ResultItem("köpek", false, emptyList())
            )
        )
        assertEquals(2, withWords.totalWords)
    }
}
