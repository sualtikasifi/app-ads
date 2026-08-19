package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.sualtikasifi.cizimhafiza.domain.model.DrawingPoint
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.presentation.theme.PenColor
import kotlin.math.sqrt

/** Which tool [DrawableCanvas] is currently interpreting drag gestures as. */
enum class DrawTool { PEN, ERASER }

private const val ERASE_TOUCH_RADIUS_PX = 28f

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSq = abx * abx + aby * aby
    if (lengthSq <= 0f) return kotlin.math.hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSq).coerceIn(0f, 1f)
    val projX = a.x + t * abx
    val projY = a.y + t * aby
    return sqrt((p.x - projX) * (p.x - projX) + (p.y - projY) * (p.y - projY))
}

/** True if [point] comes within [ERASE_TOUCH_RADIUS_PX] of any segment in [stroke]. */
private fun strokeHitBy(stroke: DrawingStroke, point: Offset): Boolean {
    if (stroke.isEmpty()) return false
    if (stroke.size == 1) {
        val only = Offset(stroke.first().x, stroke.first().y)
        return kotlin.math.hypot(point.x - only.x, point.y - only.y) <= ERASE_TOUCH_RADIUS_PX
    }
    for (i in 0 until stroke.size - 1) {
        val a = Offset(stroke[i].x, stroke[i].y)
        val b = Offset(stroke[i + 1].x, stroke[i + 1].y)
        if (distanceToSegment(point, a, b) <= ERASE_TOUCH_RADIUS_PX) return true
    }
    return false
}

/**
 * Read-only re-render of stored vector strokes — used on the Guess screen and
 * result thumbnails. Points are stored in the pixel coordinates of whatever
 * canvas they were originally drawn on (the full-size Drawing screen), so
 * this scales+centers the strokes' bounding box to fit whatever (possibly
 * much smaller, e.g. a Result-screen grid thumbnail) canvas they're re-drawn
 * into here — otherwise only the top-left sliver of a differently-sized
 * canvas would overlap the original drawing's coordinate range.
 */
@Composable
fun StrokeCanvas(
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f
) {
    Canvas(modifier = modifier) {
        val allPoints = strokes.asSequence().flatten()
        val minX = allPoints.minOfOrNull { it.x } ?: return@Canvas
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOfOrNull { it.y } ?: return@Canvas
        val maxY = allPoints.maxOf { it.y }
        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)

        val paddingPx = size.minDimension * 0.08f
        val availableWidth = (size.width - paddingPx * 2).coerceAtLeast(1f)
        val availableHeight = (size.height - paddingPx * 2).coerceAtLeast(1f)
        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val drawOffsetX = (size.width - contentWidth * scale) / 2f
        val drawOffsetY = (size.height - contentHeight * scale) / 2f

        fun toOffset(point: DrawingPoint) = Offset(
            x = drawOffsetX + (point.x - minX) * scale,
            y = drawOffsetY + (point.y - minY) * scale
        )

        strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            // A stationary tap (e.g. a quick reminder dot) never crosses
            // DrawableCanvas's drag touch-slop, so it's captured as a
            // single-point "stroke" — render it as a dot instead of a line.
            if (stroke.size == 1) {
                drawCircle(color = strokeColor, radius = strokeWidthPx / 2f, center = toOffset(stroke.first()))
                return@forEach
            }
            val path = Path().apply {
                val start = toOffset(stroke.first())
                moveTo(start.x, start.y)
                stroke.drop(1).forEach {
                    val p = toOffset(it)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/**
 * Interactive drawing surface. Points are captured as raw [Offset]s while
 * dragging and only converted/emitted as a finished [DrawingStroke] on
 * drag-end, so the ViewModel only ever sees complete strokes.
 *
 * [onStrokeProgress] additionally reports the in-progress stroke on every
 * move, not just at drag-end — so if the drawing timer runs out mid-stroke
 * (finger still down), the ViewModel already has the latest partial points
 * and can save them as part of that word's drawing instead of losing them
 * or letting them bleed into the next word's canvas.
 *
 * Callers should wrap this composable in `key(wordId) { ... }` per turn so
 * its remembered drag state (and any gesture still in flight) is fully
 * discarded when a new word starts — otherwise a drag that's still active
 * when the timer flips to the next word keeps writing into the new canvas.
 */
@Composable
fun DrawableCanvas(
    liveStrokes: List<DrawingStroke>,
    onStrokeFinished: (DrawingStroke) -> Unit,
    modifier: Modifier = Modifier,
    onStrokeProgress: (DrawingStroke) -> Unit = {},
    tool: DrawTool = DrawTool.PEN,
    onEraseStroke: (DrawingStroke) -> Unit = {},
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f
) {
    var inProgress by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            // Keyed on `tool` so switching between pen/eraser tears down and
            // re-attaches gesture detection cleanly instead of a drag begun
            // under one tool being interpreted under the other mid-gesture.
            .pointerInput(tool) {
                if (tool == DrawTool.ERASER) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            liveStrokes.firstOrNull { strokeHitBy(it, change.position) }
                                ?.let(onEraseStroke)
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = { offset ->
                            inProgress = listOf(offset)
                            onStrokeProgress(inProgress.map { DrawingPoint(it.x, it.y) })
                        },
                        onDrag = { change, _ ->
                            inProgress = inProgress + change.position
                            onStrokeProgress(inProgress.map { DrawingPoint(it.x, it.y) })
                        },
                        onDragEnd = {
                            if (inProgress.size >= 2) {
                                onStrokeFinished(inProgress.map { DrawingPoint(it.x, it.y) })
                            }
                            inProgress = emptyList()
                            onStrokeProgress(emptyList())
                        }
                    )
                }
            }
            // A stationary tap never moves past detectDragGestures' touch
            // slop, so it wouldn't reach onDragStart/onDragEnd at all —
            // without this, a quick "reminder dot" tap would be silently
            // lost instead of saved as a single-point stroke. Eraser mode
            // has no equivalent single-tap behavior — a tap alone doesn't
            // erase anything, only a drag that actually crosses a stroke.
            .pointerInput(tool) {
                if (tool == DrawTool.PEN) {
                    detectTapGestures(
                        onTap = { offset -> onStrokeFinished(listOf(DrawingPoint(offset.x, offset.y))) }
                    )
                }
            }
    ) {
        (liveStrokes + listOf(inProgress.map { DrawingPoint(it.x, it.y) })).forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            if (stroke.size == 1) {
                drawCircle(color = strokeColor, radius = strokeWidthPx / 2f, center = Offset(stroke.first().x, stroke.first().y))
                return@forEach
            }
            val path = Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}
