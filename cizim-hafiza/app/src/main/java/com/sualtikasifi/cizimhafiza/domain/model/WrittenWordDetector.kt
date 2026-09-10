package com.sualtikasifi.cizimhafiza.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recognises a round in which the player WROTE each word instead of drawing
 * it, so that round can be kept out of the Hızlı Eşleş opponent pool.
 *
 * ### Why this exists, and what it deliberately is not
 *
 * A player guesses their OWN drawing (see GameViewModel's guess phase), so
 * writing the word is perfect recall every time. In solo, level and offline
 * play that cheats nobody but the player. There is exactly one place it
 * reaches somebody else: a recorded round is served to a stranger as a Hızlı
 * Eşleş opponent, and an unbeatable stranger is the version of that mode
 * nobody opens twice.
 *
 * That narrows the job enormously. This is NOT an anti-cheat system and
 * nothing here punishes anybody: a flagged round still pays out its full
 * score, XP, streak and achievements, and the player is told nothing. The
 * single consequence is that the round is not recorded as an opponent. Being
 * wrong therefore costs one unrecorded round, which is why the thresholds
 * below can be tuned for near-zero false positives without agonising over
 * the ones that get through.
 *
 * It also runs on the client, so a modified APK skips it entirely. That is
 * accepted: this filters casual cheating, and real enforcement would need a
 * server-side check that the current player base does not justify.
 *
 * ### The two things that make it work
 *
 * **Judge the round, not the drawing.** Every per-drawing rule here has
 * honest counter-examples — a fence, a train, a row of windows. Measured
 * against 1148 real hand-drawn words, the worst offenders were *domino taşı*
 * (a left-to-right row of identical dots), *salatalık*, *diş fırçası* and
 * *taş kağıt makas*: all genuine, all scoring as high as writing does. What
 * separates them from cheating is frequency. A cheat writes EVERY word; a
 * cucumber happens once in ten. Requiring [FLAGGED_WORDS_REQUIRED] of a
 * ten-word round takes a 0.44% per-word false-positive rate down to roughly
 * one round in thirteen million, while still catching ~98% of written ones.
 *
 * **Group strokes into letters first.** Turkish is full of characters whose
 * dots and diacritics are separate strokes — İ, Ö, Ü, Ç, Ş, Ğ, plus the
 * crossbar of A, E and H. Per-stroke height and baseline statistics are
 * wrecked by them: an earlier version of this that measured raw strokes
 * recognised one written word in twelve. Merging horizontally overlapping
 * strokes into letter clusters first took that to eight in twelve, with no
 * other change.
 *
 * ### Coordinate space
 *
 * Points are stored in the pixel coordinates of whatever canvas they were
 * drawn on (see StrokeCanvas), so a tablet and a phone produce different
 * numbers for the same gesture. Every measurement below is therefore a ratio
 * against the drawing's own bounding box, and never an absolute distance.
 */
object WrittenWordDetector {

    /**
     * Fraction of the narrower stroke's width that two strokes must share
     * horizontally to count as the same letter.
     *
     * Tuned to catch a dot sitting above its stem (which overlaps almost
     * completely) without swallowing the neighbouring letter (which normally
     * does not overlap at all, and where it does, a written word is not
     * "tidy" and fails [tidy] instead).
     */
    private const val SAME_LETTER_OVERLAP = 0.45f

    /** A cluster count this far from the word's letter count still counts as matching. */
    private const val LETTER_COUNT_TOLERANCE = 0.25f

    /**
     * How many of a round's words must look written before the round is
     * refused. See the class KDoc for where this number comes from — it is
     * the whole reason the per-word rate is allowed to be as loose as it is.
     */
    const val FLAGGED_WORDS_REQUIRED = 4

    /**
     * The bar when the round's OUTCOME independently says the same thing —
     * see [outcomeLooksRead]. Two weak signals agreeing is worth more than
     * either alone, so the geometric bar comes down by one.
     */
    const val FLAGGED_WORDS_REQUIRED_WITH_OUTCOME = 3

    /** A word is flagged at seven of the eight clauses in [writingScore]. */
    private const val FLAG_AT = 0.875f

    /**
     * Reading your own handwriting is faster than remembering a drawing.
     *
     * Deliberately well under the app's own idea of a realistic human answer
     * (BotGhostRuns hands its bot 1200–3500 ms) because this must not catch
     * players who are merely quick. It is also never used on its own: a
     * perfect fast round only lowers the geometric bar, it cannot flag a
     * round by itself. Doing otherwise would quietly drop the best players'
     * rounds out of the pool and bias every opponent downwards.
     */
    private const val READ_RATHER_THAN_RECALLED_MS = 900L

    /** One stroke's bounding box, and by extension one letter cluster's. */
    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Whether this whole round should be kept out of the pool.
     *
     * [perWord] is only consulted for [outcomeLooksRead]; a round with no
     * timing information is judged on geometry alone.
     */
    fun roundLooksWritten(items: List<ResultItem>, perWord: List<GhostRunWord>): Boolean {
        if (items.isEmpty()) return false
        val flagged = items.count { looksWritten(it) }
        val required = if (outcomeLooksRead(perWord)) {
            FLAGGED_WORDS_REQUIRED_WITH_OUTCOME
        } else {
            FLAGGED_WORDS_REQUIRED
        }
        return flagged >= required
    }

    /** Whether one drawing, on its own, has the shape of written text. */
    fun looksWritten(item: ResultItem): Boolean =
        writingScore(item.strokes, letterCount(item.word)) >= FLAG_AT

    /**
     * Every word recalled correctly, and faster than recall plausibly
     * happens. Never a verdict by itself — see [READ_RATHER_THAN_RECALLED_MS].
     */
    fun outcomeLooksRead(perWord: List<GhostRunWord>): Boolean {
        if (perWord.isEmpty()) return false
        if (perWord.any { !it.isCorrect }) return false
        val times = perWord.map { it.responseTimeMs }.sorted()
        val median = if (times.size % 2 == 1) {
            times[times.size / 2]
        } else {
            (times[times.size / 2 - 1] + times[times.size / 2]) / 2
        }
        return median < READ_RATHER_THAN_RECALLED_MS
    }

    /**
     * How many of the eight independent "this is writing" clauses hold, as
     * 0..1. The clauses are scored rather than required together because no
     * single one of them survives contact with real drawings — see the
     * class KDoc.
     *
     * [letters] is the letter count of the word the player was asked to
     * draw. It is the strongest clause available and it exists only because
     * of this game's shape: we know the answer, so we can ask whether the
     * drawing decomposes into exactly that many letter-sized pieces. Pass
     * null (or zero) when the word is unknown and the remaining seven
     * clauses are scored on their own.
     */
    fun writingScore(strokes: List<DrawingStroke>, letters: Int?): Float {
        val boxes = strokes.filter { it.size >= 2 }.map { it.boundingBox() }
        if (boxes.size < 2) return 0f

        val page = Box(
            left = boxes.minOf { it.left },
            top = boxes.minOf { it.top },
            right = boxes.maxOf { it.right },
            bottom = boxes.maxOf { it.bottom }
        )
        val width = max(page.width, EPSILON)
        val height = max(page.height, EPSILON)

        val clusters = clusterIntoLetters(boxes)
        // A drawing that will not separate into at least two pieces is a
        // connected shape, which is the one thing a written word is not.
        if (clusters.size < 2) return 0f

        var held = 0
        // Clusters come out of clusterIntoLetters already sorted by left
        // edge, so this asks whether their CENTRES advance in that same
        // order — i.e. whether they tile left to right rather than nest.
        val centres = clusters.map { (it.left + it.right) / 2f }
        val advancing = (0 until centres.size - 1).count { centres[it + 1] > centres[it] }
        if (advancing.toFloat() / (centres.size - 1) >= 0.85f) held++

        // Letters sit on a shared baseline and reach a shared cap height.
        if (standardDeviation(clusters.map { it.bottom }) / height <= 0.22f) held++
        if (standardDeviation(clusters.map { it.top }) / height <= 0.28f) held++

        // ...and are all about the same size, unlike the parts of a drawing.
        val heights = clusters.map { max(it.height, EPSILON) }
        if (standardDeviation(heights) / max(heights.average().toFloat(), EPSILON) <= 0.35f) held++

        // A written word is a wide, short band, and each letter is a small
        // slice of it.
        if (width / height >= 1.6f) held++
        if (clusters.map { it.width / width }.average() <= 0.32f) held++

        // Letters queue up; they do not overlap each other.
        val tidy = (0 until clusters.size - 1).count {
            (clusters[it + 1].left - clusters[it].right) / width > -0.05f
        }
        if (tidy.toFloat() / (clusters.size - 1) >= 0.80f) held++

        val clauses = if (letters != null && letters > 0) {
            if (abs(clusters.size - letters).toFloat() / letters <= LETTER_COUNT_TOLERANCE) held++
            8
        } else {
            7
        }
        return held.toFloat() / clauses
    }

    /**
     * Merges strokes that occupy the same horizontal column into one letter.
     *
     * This is the step that makes the rest of the measurements mean
     * anything in Turkish — see the class KDoc on İ, Ö, Ç, Ş and Ğ.
     */
    private fun clusterIntoLetters(boxes: List<Box>): List<Box> {
        val byLeft = boxes.sortedBy { it.left }
        val clusters = mutableListOf<Box>()
        for (box in byLeft) {
            val open = clusters.lastOrNull()
            if (open != null) {
                val shared = min(open.right, box.right) - max(open.left, box.left)
                val narrower = max(min(open.width, box.width), EPSILON)
                if (shared > SAME_LETTER_OVERLAP * narrower) {
                    clusters[clusters.size - 1] = Box(
                        left = min(open.left, box.left),
                        top = min(open.top, box.top),
                        right = max(open.right, box.right),
                        bottom = max(open.bottom, box.bottom)
                    )
                    continue
                }
            }
            clusters += box
        }
        return clusters
    }

    private fun DrawingStroke.boundingBox(): Box = Box(
        left = minOf { it.x },
        top = minOf { it.y },
        right = maxOf { it.x },
        bottom = maxOf { it.y }
    )

    /**
     * Letters only — spaces, hyphens and apostrophes are not drawn as
     * letter-shaped clusters, so counting them would make multi-word
     * prompts like "taş kağıt makas" look like a mismatch.
     */
    private fun letterCount(word: String): Int = word.count { it.isLetter() }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.sumOf { val d = it - mean; d * d } / values.size
        return sqrt(variance).toFloat()
    }

    private const val EPSILON = 1e-6f
}
