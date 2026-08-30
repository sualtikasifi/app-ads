package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Horizontal bias cycled per row so a column of nodes reads as a winding
 * road (Candy Crush's map style) instead of a straight vertical stack.
 * Shared between WorldMapScreen and LevelMapScreen so both use the exact
 * same curve and both node columns line up with [WindingPathCanvas] below.
 */
val WindingPathBiasCycle = listOf(-0.6f, 0f, 0.6f, 0f)

/**
 * The dashed connecting line drawn *behind* a column of nodes. [itemCount]
 * must match the number of node rows above it, and this composable's
 * modifier must size it to exactly `rowHeight * itemCount` so the curve's
 * knots land under each node — see LevelMapScreen/WorldMapScreen for the
 * matching row layout.
 */
@Composable
fun WindingPathCanvas(itemCount: Int, rowHeight: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(rowHeight * itemCount)) {
        if (itemCount < 2) return@Canvas
        val rowHeightPx = size.height / itemCount
        // 0.4 (not 0.5) keeps the curve's widest swing inboard of the node
        // circles at the screen edges, matching where WindingPathBiasCycle
        // visually centers each row's node.
        val points = (0 until itemCount).map { i ->
            val bias = WindingPathBiasCycle[i % WindingPathBiasCycle.size]
            Offset(
                x = size.width / 2f + bias * size.width * 0.4f,
                y = rowHeightPx * i + rowHeightPx / 2f
            )
        }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midY = (prev.y + curr.y) / 2f
                cubicTo(prev.x, midY, curr.x, midY, curr.x, curr.y)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 14f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 20f))
            )
        )
    }
}
