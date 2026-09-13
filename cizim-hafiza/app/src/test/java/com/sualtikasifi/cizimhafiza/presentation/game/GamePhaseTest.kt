package com.sualtikasifi.cizimhafiza.presentation.game

import com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GamePhase.Result] and [DailyResultSummary] are marked @Serializable so a
 * finished match can be checkpointed into SavedStateHandle and redisplayed
 * verbatim after process death, without ever re-running finishGame() (which
 * would re-save the session and re-grant XP) — see GameViewModel's and
 * OnlineGameViewModel's recovery snapshots. If this stops round-tripping,
 * a process death on the result screen would crash or silently drop the
 * checkpoint instead of just redisplaying the same score.
 */
class GamePhaseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a free-play Result survives a JSON round trip`() {
        val result = GamePhase.Result(
            totalScore = 35,
            correctCount = 6,
            wrongCount = 1,
            fastestCorrectSeconds = 1.8,
            items = listOf(
                ResultItem("gemi", true, listOf(listOf(DrawingPoint(0f, 0f), DrawingPoint(1f, 1f)))),
                ResultItem("kale", false, emptyList())
            )
        )
        val decoded = json.decodeFromString<GamePhase.Result>(json.encodeToString(result))
        assertEquals(result, decoded)
    }

    @Test
    fun `a daily-challenge Result carries its DailyResultSummary through a round trip`() {
        val result = GamePhase.Result(
            totalScore = 50,
            correctCount = 10,
            wrongCount = 0,
            fastestCorrectSeconds = null,
            items = emptyList(),
            levelStars = 3,
            daily = DailyResultSummary(
                streak = 5,
                xpEarned = 120,
                streakMultiplierIncreased = true,
                streakMultiplier = 4
            )
        )
        val decoded = json.decodeFromString<GamePhase.Result>(json.encodeToString(result))
        assertEquals(result, decoded)
    }
}
