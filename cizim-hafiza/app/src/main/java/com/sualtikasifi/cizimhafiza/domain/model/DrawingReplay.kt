package com.sualtikasifi.cizimhafiza.domain.model

import kotlin.math.roundToInt

/**
 * The timing a stored drawing is replayed at.
 *
 * **The tempo is synthetic, and it has to be.** [DrawingPoint] carries x and
 * y and nothing else — no timestamp was ever recorded, on any drawing,
 * including every round already sitting in the pool. The true speed of a
 * drawing is therefore not recoverable, and adding a timestamp now would
 * still leave every existing drawing without one (while making each point
 * roughly half as big again to store). What IS recorded, and what a replay
 * reproduces faithfully, is the ORDER: which stroke came first, and which
 * way the finger travelled along it.
 *
 * Domain-level rather than living next to one renderer because there are
 * two: the on-screen replay and the exported video. If they computed their
 * own timing the video would not be a recording of what the reviewer
 * actually watched.
 */
object DrawingReplay {

    /**
     * Charged at the end of every stroke but the last, so the pen visibly
     * lifts between strokes instead of the whole drawing arriving as one
     * continuous scribble. Roughly a frame's worth of pause.
     */
    const val PEN_LIFT_UNITS = 6

    private const val BASE_DURATION_MS = 500f
    private const val MS_PER_UNIT = 5f

    /**
     * A drawing of two quick lines still deserves to be watched rather than
     * flashed, and a dense one has to stay inside the few seconds anybody
     * will sit through on a result screen.
     */
    private const val MIN_DURATION_MS = 1_200
    private const val MAX_DURATION_MS = 3_500

    /**
     * The length of the playback timeline: every point to be drawn, plus the
     * pen-lift gaps between strokes. Measured in units rather than points so
     * that the gaps are part of the timeline and the drawing still finishes
     * exactly as progress reaches 1.
     */
    fun timelineUnits(strokes: List<DrawingStroke>): Int {
        val points = strokes.sumOf { it.size }
        if (points == 0) return 0
        return points + PEN_LIFT_UNITS * (strokes.size - 1).coerceAtLeast(0)
    }

    /** How long a drawing of [totalUnits] should take to replay. */
    fun durationMillis(totalUnits: Int): Int =
        (BASE_DURATION_MS + totalUnits * MS_PER_UNIT).roundToInt()
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    /**
     * Walks the strokes visible at [progress], handing each one the number
     * of its points that have been drawn so far.
     *
     * Inline and callback-shaped rather than returning rebuilt stroke lists:
     * this runs once per frame inside a draw scope, where an allocation per
     * frame is exactly what a draw scope is not for.
     */
    inline fun forEachVisible(
        strokes: List<DrawingStroke>,
        totalUnits: Int,
        progress: Float,
        action: (stroke: DrawingStroke, visiblePoints: Int) -> Unit
    ) {
        var remaining = totalUnits * progress
        for (stroke in strokes) {
            if (remaining <= 0f) return
            val visible = remaining.toInt().coerceIn(0, stroke.size)
            if (visible > 0) action(stroke, visible)
            if (visible < stroke.size) return
            remaining -= stroke.size + PEN_LIFT_UNITS
        }
    }
}
