package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private data class ScatterSpot(
    val xFraction: Float,
    val yFraction: Float,
    val emoji: String,
    val sizeSp: Float,
    val alpha: Float,
    val rotation: Float
)

/**
 * Purely code-drawn map backdrop (no illustration assets — see World.kt's
 * emoji-based theming) — a soft gradient plus a handful of the given emoji
 * scattered at low opacity, so each world's level map (and the world map
 * itself) feels like a themed "land" without needing sourced artwork.
 * Positions are seeded from [emojis] so they're stable across recompositions
 * but still differ world to world.
 */
@Composable
fun ThemedMapBackground(
    emojis: List<String>,
    gradientColors: List<Color>,
    contentHeight: Dp,
    modifier: Modifier = Modifier,
    decorationCount: Int = 18
) {
    val spots = remember(emojis, decorationCount) {
        val random = Random(emojis.hashCode())
        List(decorationCount) {
            val rawX = random.nextFloat()
            // Nudge anything too close to the center band out of the way —
            // that's roughly where the winding path's nodes sit, and a
            // decoration hidden directly behind one is wasted.
            val xFraction = if (rawX in 0.35f..0.65f) {
                (if (random.nextBoolean()) rawX - 0.28f else rawX + 0.28f).coerceIn(0.02f, 0.98f)
            } else rawX
            ScatterSpot(
                xFraction = xFraction,
                yFraction = random.nextFloat(),
                emoji = emojis[random.nextInt(emojis.size)],
                sizeSp = 24f + random.nextFloat() * 26f,
                alpha = 0.10f + random.nextFloat() * 0.14f,
                rotation = random.nextFloat() * 40f - 20f
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight)
            .background(Brush.verticalGradient(gradientColors))
    ) {
        spots.forEach { spot ->
            Text(
                text = spot.emoji,
                fontSize = spot.sizeSp.sp,
                modifier = Modifier
                    .offset(x = maxWidth * spot.xFraction, y = contentHeight * spot.yFraction)
                    .graphicsLayer(alpha = spot.alpha, rotationZ = spot.rotation)
            )
        }
    }
}
