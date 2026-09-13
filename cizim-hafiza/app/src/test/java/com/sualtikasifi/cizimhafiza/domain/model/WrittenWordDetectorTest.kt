package com.sualtikasifi.cizimhafiza.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [WrittenWordDetector] against real data rather than invented data.
 *
 * `real_drawings.json` is 100 genuinely hand-drawn words lifted out of the
 * bot training set (the same 1148 drawings the thresholds were calibrated
 * on), deliberately weighted towards the highest-scoring ones — every drawing
 * that came close to being mistaken for writing is in here, so a change that
 * loosens the detector shows up as a failure rather than as a silent rise in
 * false positives.
 *
 * `written_words.json` is synthesised handwriting: block letters laid out
 * left to right with human jitter, dots and diacritics as their own strokes.
 * Its limitation is worth stating plainly — it is not real handwriting
 * captured through the app's canvas, so the recall it measures here is an
 * estimate. Cursive, in particular, would arrive as one connected stroke and
 * is not represented at all.
 *
 * `expectedScore` on both fixtures is the score the calibrated reference
 * implementation produced. Asserting against it pins the Kotlin port to the
 * numbers the thresholds were actually chosen from: a porting slip that
 * changed a ratio somewhere would otherwise pass every behavioural test here
 * while quietly moving the false-positive rate.
 */
class WrittenWordDetectorTest {

    @Serializable
    private data class Fixture(
        val word: String,
        val expectedScore: Float,
        val strokes: List<List<Point>>
    )

    @Serializable
    private data class Point(val x: Float, val y: Float)

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(resource: String): List<Fixture> {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource)) {
            "Missing test fixture $resource"
        }.bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }

    private fun Fixture.strokeList(): List<DrawingStroke> =
        strokes.map { stroke -> stroke.map { DrawingPoint(it.x, it.y) } }

    private fun Fixture.letters(): Int = word.count { it.isLetter() }

    private fun Fixture.asItem(): ResultItem =
        ResultItem(word = word, isCorrect = true, strokes = strokeList())

    // ---- the port matches the calibrated reference ------------------------

    @Test
    fun `scores real drawings exactly as the calibrated reference did`() {
        load("real_drawings.json").forEach { fixture ->
            assertEquals(
                "Score drifted for '${fixture.word}'",
                fixture.expectedScore.toDouble(),
                WrittenWordDetector.writingScore(fixture.strokeList(), fixture.letters()).toDouble(),
                0.001
            )
        }
    }

    @Test
    fun `scores written words exactly as the calibrated reference did`() {
        load("written_words.json").forEach { fixture ->
            assertEquals(
                "Score drifted for '${fixture.word}'",
                fixture.expectedScore.toDouble(),
                WrittenWordDetector.writingScore(fixture.strokeList(), fixture.letters()).toDouble(),
                0.001
            )
        }
    }

    // ---- the property that actually matters -------------------------------

    @Test
    fun `real drawings are almost never mistaken for writing`() {
        val real = load("real_drawings.json")
        val flagged = real.count { WrittenWordDetector.looksWritten(it.asItem()) }
        // This fixture over-samples the worst cases on purpose, so the rate
        // here is far above the 0.44% measured across all 1148 drawings.
        // The assertion guards the direction, not the ratio.
        assertTrue(
            "Too many real drawings flagged: $flagged of ${real.size}",
            flagged <= 6
        )
    }

    @Test
    fun `most written words are recognised`() {
        val written = load("written_words.json")
        val flagged = written.count { WrittenWordDetector.looksWritten(it.asItem()) }
        assertTrue("Only $flagged of ${written.size} written words recognised", flagged >= 8)
    }

    // ---- round-level aggregation, which is the real decision --------------

    @Test
    fun `a round of real drawings is recorded`() {
        val round = ordinaryRound()
        assertFalse(WrittenWordDetector.roundLooksWritten(round, recallTimings(round.size)))
    }

    @Test
    fun `a round of written words is refused`() {
        val round = load("written_words.json").take(GhostRuns.RUN_WORD_COUNT).map { it.asItem() }
        assertTrue(WrittenWordDetector.roundLooksWritten(round, recallTimings(round.size)))
    }

    @Test
    fun `one suspicious drawing in an honest round is not enough`() {
        val real = load("real_drawings.json")
        // The single worst real drawing, padded out with ordinary ones —
        // this is the cucumber-in-a-fence case the round rule exists for.
        val round = (listOf(real.first()) + real.takeLast(GhostRuns.RUN_WORD_COUNT - 1))
            .map { it.asItem() }
        assertTrue("Fixture should lead with a flagged drawing", WrittenWordDetector.looksWritten(round.first()))
        assertFalse(WrittenWordDetector.roundLooksWritten(round, recallTimings(round.size)))
    }

    // ---- the outcome signal never decides on its own ----------------------

    @Test
    fun `a perfect fast round of real drawings is still recorded`() {
        val round = ordinaryRound()
        val read = List(round.size) { GhostRunWord(it, isCorrect = true, responseTimeMs = 400, pointsAwarded = 5) }
        assertTrue("Fixture should read as suspiciously fast", WrittenWordDetector.outcomeLooksRead(read))
        assertFalse(
            "Speed alone must never refuse a round — that would drop the best players",
            WrittenWordDetector.roundLooksWritten(round, read)
        )
    }

    @Test
    fun `a missed word means the round was not simply read`() {
        val timings = listOf(
            GhostRunWord(1, isCorrect = true, responseTimeMs = 300, pointsAwarded = 5),
            GhostRunWord(2, isCorrect = false, responseTimeMs = 300, pointsAwarded = 0)
        )
        assertFalse(WrittenWordDetector.outcomeLooksRead(timings))
    }

    // ---- degenerate input -------------------------------------------------

    @Test
    fun `a drawing that will not separate is never writing`() {
        val blob = listOf(
            listOf(DrawingPoint(0f, 0f), DrawingPoint(50f, 50f), DrawingPoint(0f, 50f), DrawingPoint(0f, 0f))
        )
        assertEquals(0f, WrittenWordDetector.writingScore(blob, letters = 4), 0.0001f)
    }

    @Test
    fun `empty and single-point input score zero rather than throwing`() {
        assertEquals(0f, WrittenWordDetector.writingScore(emptyList(), letters = 4), 0.0001f)
        assertEquals(
            0f,
            WrittenWordDetector.writingScore(listOf(listOf(DrawingPoint(1f, 1f))), letters = 4),
            0.0001f
        )
        assertFalse(WrittenWordDetector.roundLooksWritten(emptyList(), emptyList()))
    }

    @Test
    fun `an unknown word is scored on the remaining seven clauses`() {
        val fixture = load("written_words.json").first { it.word == "ARABA" }
        // Scored out of seven instead of eight, so it must not crash and must
        // still land high — the letter-count clause is the strongest single
        // signal but it is not the only one.
        val score = WrittenWordDetector.writingScore(fixture.strokeList(), letters = null)
        assertTrue("Unknown-word score was $score", score >= 0.85f)
    }

    /**
     * Ten ordinary real drawings.
     *
     * Taken from the TAIL on purpose: the fixture is sorted worst-first, so
     * the head is the adversarial sample used by the per-drawing tests and
     * taking ten of those would be a round of ten cucumbers — which the
     * detector rightly refuses, and which says nothing about a real round.
     */
    private fun ordinaryRound(): List<ResultItem> =
        load("real_drawings.json").takeLast(GhostRuns.RUN_WORD_COUNT).map { it.asItem() }

    /**
     * Timings that look like genuine recall, so a geometry-only verdict is
     * what the round-level tests are actually measuring.
     */
    private fun recallTimings(count: Int): List<GhostRunWord> =
        List(count) { GhostRunWord(it, isCorrect = it % 4 != 0, responseTimeMs = 2_400, pointsAwarded = 5) }
}
