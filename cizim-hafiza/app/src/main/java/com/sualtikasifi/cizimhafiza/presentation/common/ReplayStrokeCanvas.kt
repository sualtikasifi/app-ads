package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.presentation.theme.PenColor
import kotlin.math.roundToInt

/**
 * Replays a stored drawing stroke by stroke, the way it was drawn.
 *
 * Costs nothing to run: this is the same [DrawingStroke] list [StrokeCanvas]
 * is already handed, rendered a prefix at a time instead of all at once.
 * Nothing is fetched, stored or uploaded to replay a drawing.
 *
 * **The tempo is synthetic, and it has to be.** [com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint]
 * carries x and y and nothing else — no timestamp was ever recorded, on any
 * drawing, including every round already sitting in the pool. So the true
 * speed of a drawing is simply not recoverable, and adding a timestamp now
 * would still leave every existing drawing without one (while making each
 * point roughly half as big again to store). What IS recorded, and what this
 * replays faithfully, is the ORDER: which stroke came first, and which way
 * the finger travelled along it. That is the part worth watching.
 *
 * Playback advances at a constant rate through a timeline measured in
 * points, with a short gap charged at each stroke end so the pen visibly
 * lifts between strokes instead of one continuous scribble.
 */
@Composable
fun ReplayStrokeCanvas(
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f,
    penSkin: PenSkin? = null,
    /**
     * Change this to replay from the beginning — e.g. a "tekrar oynat"
     * button incrementing a counter. Playback also starts on first
     * composition and whenever [strokes] changes.
     */
    playToken: Int = 0,
    /** Fires once the drawing is fully on screen, so a caller can reveal controls. */
    onFinished: () -> Unit = {}
) {
    // Units, not points: the pen-lift gaps are part of the timeline, so the
    // whole drawing still finishes exactly when progress reaches 1.
    val totalUnits = remember(strokes) { strokes.timelineUnits() }
    val durationMs = remember(totalUnits) {
        (BASE_DURATION_MS + totalUnits * MS_PER_UNIT).roundToInt()
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    val progress = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(strokes, playToken) {
        if (totalUnits <= 0) {
            currentOnFinished()
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        // Linear on purpose: an eased curve would have the hand speed up and
        // slow down in places the original drawing did not, which reads as a
        // claim about how it was drawn rather than an animation choice.
        progress.animateTo(1f, tween(durationMillis = durationMs, easing = LinearEasing))
        currentOnFinished()
    }

    Canvas(modifier = modifier) {
        val fit = strokeFitFor(strokes) ?: return@Canvas
        val paint: Brush = penSkin?.let { penBrush(it, size.width, size.height) } ?: SolidColor(strokeColor)

        // progress.value is read HERE, inside the draw scope, and nowhere in
        // the composable body above. Read during composition it would
        // re-compose this whole subtree on every one of the ~60 frames a
        // replay lasts; read here it only re-runs the draw phase, which is
        // all that actually changes.
        var remaining = totalUnits * progress.value

        for (stroke in strokes) {
            if (remaining <= 0f) break
            val visiblePoints = remaining.toInt().coerceIn(0, stroke.size)
            if (visiblePoints > 0) {
                drawFittedStroke(
                    stroke = if (visiblePoints == stroke.size) stroke else stroke.subList(0, visiblePoints),
                    fit = fit,
                    paint = paint,
                    strokeWidthPx = strokeWidthPx
                )
            }
            if (visiblePoints < stroke.size) break
            remaining -= stroke.size + PEN_LIFT_UNITS
        }
    }
}

/** Points to draw plus the pen-lift gaps between strokes — see [ReplayStrokeCanvas]. */
private fun List<DrawingStroke>.timelineUnits(): Int {
    val points = sumOf { it.size }
    if (points == 0) return 0
    return points + PEN_LIFT_UNITS * (size - 1).coerceAtLeast(0)
}

/** Roughly a frame's worth of pause at each pen lift. */
private const val PEN_LIFT_UNITS = 6

private const val BASE_DURATION_MS = 500f
private const val MS_PER_UNIT = 5f

/**
 * A drawing of two quick lines still deserves to be watched rather than
 * flashed, and a dense one has to stay inside the few seconds anybody will
 * sit through on a result screen.
 */
private const val MIN_DURATION_MS = 1_200
private const val MAX_DURATION_MS = 3_500

/**
 * A drawing that plays itself once and then offers to play again.
 *
 * The wrapper exists so the three full-screen previews that show a single
 * drawing — the solo result screen, the online result screen and the review
 * panel — get identical behaviour from one line each, rather than three
 * copies of the same playback state drifting apart.
 *
 * The replay button only appears once playback has finished. Shown during
 * playback it would sit on top of the drawing it is there to reveal, and
 * there would be nothing to replay yet.
 *
 * Only ever used for a drawing shown ON ITS OWN. The result grid keeps the
 * plain [StrokeCanvas]: ten thumbnails animating at once is a lot of
 * per-frame drawing for something the eye cannot follow anyway.
 */
@Composable
fun ReplayableDrawing(
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f,
    penSkin: PenSkin? = null
) {
    // Keyed on the drawing: opening a different one starts its own playback
    // from zero instead of inheriting the previous drawing's finished state
    // and showing a replay button over a blank canvas.
    var playToken by remember(strokes) { mutableIntStateOf(0) }
    var finished by remember(strokes) { mutableStateOf(false) }

    Box(modifier = modifier) {
        ReplayStrokeCanvas(
            strokes = strokes,
            modifier = Modifier.matchParentSize(),
            strokeColor = strokeColor,
            strokeWidthPx = strokeWidthPx,
            penSkin = penSkin,
            playToken = playToken,
            onFinished = { finished = true }
        )
        if (finished) {
            RaisedIconButton(
                icon = Icons.Filled.Replay,
                contentDescription = stringResource(R.string.replay_drawing),
                onClick = {
                    finished = false
                    playToken++
                },
                size = 38.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
            )
        }
    }
}
