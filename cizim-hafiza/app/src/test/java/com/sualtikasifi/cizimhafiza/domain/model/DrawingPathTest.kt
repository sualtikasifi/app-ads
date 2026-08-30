package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Word] and [DrawingResult] are marked @Serializable so GameViewModel and
 * OnlineGameViewModel can JSON-encode a not-yet-finished match into
 * SavedStateHandle and decode it back after process death (see their
 * recovery snapshot classes). If either type ever stops round-tripping —
 * a field is added without a default, say — that recovery path silently
 * starts losing every in-progress match on the next process death instead
 * of failing loudly, so this is worth pinning down directly.
 */
class DrawingPathTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Word survives a JSON round trip`() {
        val word = Word(id = 42, text = "gemi", category = "araclar", difficulty = Difficulty.MEDIUM)
        val decoded = json.decodeFromString<Word>(json.encodeToString(word))
        assertEquals(word, decoded)
    }

    @Test
    fun `DrawingResult with strokes and an answer survives a JSON round trip`() {
        val result = DrawingResult(
            sessionId = 0L,
            wordId = 7,
            word = Word(id = 7, text = "balon", category = "eglence", difficulty = Difficulty.EASY),
            strokes = listOf(
                listOf(DrawingPoint(0f, 0f), DrawingPoint(12.5f, 30f)),
                listOf(DrawingPoint(5f, 5f))
            ),
            userAnswer = "balon",
            isCorrect = true,
            responseTimeMs = 2_450L,
            pointsAwarded = 7
        )
        val decoded = json.decodeFromString<DrawingResult>(json.encodeToString(result))
        assertEquals(result, decoded)
    }

    @Test
    fun `an unanswered DrawingResult keeps its default fields through a round trip`() {
        val result = DrawingResult(
            sessionId = 0L,
            wordId = 3,
            word = Word(id = 3, text = "kale", category = "yapi", difficulty = Difficulty.HARD),
            strokes = emptyList()
        )
        val decoded = json.decodeFromString<DrawingResult>(json.encodeToString(result))
        assertEquals("", decoded.userAnswer)
        assertEquals(false, decoded.isCorrect)
        assertEquals(0L, decoded.responseTimeMs)
        assertEquals(0, decoded.pointsAwarded)
        assertEquals(result.word, decoded.word)
    }
}
