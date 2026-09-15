package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pulsing gold halo plus a scatter of orbiting, twinkling sparkles — drawn
 * BEHIND (and past the edge of) whatever "you are here" marker it's paired
 * with: the current world on the Bölümler map, the next level on a world's
 * own path. Both maps otherwise marked that spot with nothing more than a
 * thin border, which a player skimming a long path of near-identical nodes
 * could miss entirely; a moving glow is the one cue that still catches the
 * eye at a glance, the same "this is the one that matters" language an MMO
 * uses for a quest marker.
 *
 * Draws inside whatever box [modifier] sizes — pass a size noticeably
 * larger than the marker itself (see call sites) so the glow and sparkles
 * have room to extend past its edge instead of sitting flush against it,
 * and centre this Canvas and the marker on the same point.
 */
@Composable
fun CurrentPositionGlow(modifier: Modifier = Modifier, sparkleCount: Int = 7) {
    val transition = rememberInfiniteTransition(label = "current-position-glow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "current-position-pulse"
    )
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "current-position-orbit"
    )
    val twinklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "current-position-twinkle"
    )
    val gold = AppTheme.tokens.gold
    Canvas(modifier = modifier) {
        val halfSize = size.minDimension / 2f
        // The halo sits mostly UNDER the marker (radius well below halfSize)
        // rather than ringing its outside — the sparkles orbiting further
        // out are what read as "light scattering off this spot"; the halo
        // is just what makes the marker itself look lit from within.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(gold.copy(alpha = 0.10f + 0.20f * pulse), Color.Transparent)
            ),
            radius = halfSize * (0.66f + 0.10f * pulse)
        )
        drawGlowSparkles(
            count = sparkleCount,
            halfSize = halfSize,
            baseAngleDeg = orbitAngle,
            twinklePhaseDeg = twinklePhase,
            tint = gold
        )
    }
}

private const val SPARKLE_MIN_RADIUS_FRACTION = 0.55f
private const val SPARKLE_MAX_RADIUS_FRACTION = 0.98f
private const val SPARKLE_GLYPH_FRACTION = 0.045f

/**
 * Same shape as LevelAvatar.kt's frame sparkles (deterministic per-particle
 * jitter, integer twinkle speeds so each one stays seamless across its
 * infiniteRepeatable's Restart wrap) — kept as a separate, smaller copy here
 * rather than shared, since this one is gold-tinted and sized off a whole
 * map node rather than a profile badge.
 */
private fun DrawScope.drawGlowSparkles(
    count: Int,
    halfSize: Float,
    baseAngleDeg: Float,
    twinklePhaseDeg: Float,
    tint: Color
) {
    val origin = center
    repeat(count) { i ->
        val radiusJitter = ((i * 53) % 100) / 100f
        val sizeJitter = ((i * 29) % 100) / 100f
        val brightnessCap = 0.55f + ((i * 71) % 100) / 100f * 0.45f

        val particleRadius = halfSize * (SPARKLE_MIN_RADIUS_FRACTION + radiusJitter * (SPARKLE_MAX_RADIUS_FRACTION - SPARKLE_MIN_RADIUS_FRACTION))
        val angleRad = Math.toRadians((baseAngleDeg + i * (360f / count)).toDouble())
        val point = Offset(
            origin.x + particleRadius * cos(angleRad).toFloat(),
            origin.y + particleRadius * sin(angleRad).toFloat()
        )

        val twinkleSpeed = 1 + (i % 4)
        val twinkleRad = Math.toRadians((twinklePhaseDeg * twinkleSpeed + i * 61).toDouble())
        val twinkle = (sin(twinkleRad).toFloat() + 1f) / 2f
        val dotRadius = halfSize * SPARKLE_GLYPH_FRACTION * (0.5f + sizeJitter * 0.8f) * (0.4f + twinkle * 0.8f)
        if (dotRadius <= 0f) return@repeat
        val alpha = twinkle * brightnessCap
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = alpha * 0.9f), tint.copy(alpha = 0f)),
                center = point,
                radius = dotRadius * 2.4f
            ),
            radius = dotRadius * 2.4f,
            center = point
        )
        drawCircle(color = Color.White.copy(alpha = alpha), radius = dotRadius * 0.55f, center = point)
    }
}
