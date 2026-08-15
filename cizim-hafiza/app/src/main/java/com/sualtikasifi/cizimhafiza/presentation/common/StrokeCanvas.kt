package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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

/** Read-only re-render of stored vector strokes — used on the Guess screen and result thumbnails. */
@Composable
fun StrokeCanvas(
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f
) {
    Canvas(modifier = modifier) {
        strokes.forEach { stroke ->
            if (stroke.size < 2) return@forEach
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
    strokeColor: Color = PenColor,
    strokeWidthPx: Float = 9f
) {
    var inProgress by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
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
    ) {
        (liveStrokes + listOf(inProgress.map { DrawingPoint(it.x, it.y) })).forEach { stroke ->
            if (stroke.size < 2) return@forEach
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
