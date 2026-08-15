package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.MaterialTheme
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

/** Read-only re-render of stored vector strokes — used on the Guess screen and result thumbnails. */
@Composable
fun StrokeCanvas(
    strokes: List<DrawingStroke>,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidthPx: Float = 6f
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
 */
@Composable
fun DrawableCanvas(
    liveStrokes: List<DrawingStroke>,
    onStrokeFinished: (DrawingStroke) -> Unit,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var inProgress by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> inProgress = listOf(offset) },
                onDrag = { change, _ -> inProgress = inProgress + change.position },
                onDragEnd = {
                    if (inProgress.size >= 2) {
                        onStrokeFinished(inProgress.map { DrawingPoint(it.x, it.y) })
                    }
                    inProgress = emptyList()
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
                style = Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}
