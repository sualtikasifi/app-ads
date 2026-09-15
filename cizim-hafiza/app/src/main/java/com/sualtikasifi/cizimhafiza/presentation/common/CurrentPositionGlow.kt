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
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.Orange
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pulsing halo plus a scatter of orbiting, twinkling, multi-coloured
 * sparkles — drawn BEHIND (and past the edge of) whatever "you are here"
 * marker it's paired with: the current world on the Bölümler map, the next
 * level on a world's own path, the player's own row in the global league
 * table. All three otherwise marked that spot with nothing more than a
 * thin border, which a player skimming a long list or path of near-
 * identical nodes could miss entirely; a moving glow is the one cue that
 * still catches the eye at a glance, the same "this is the one that
 * matters" language an MMO uses for a quest marker.
 *
 * Slower and stronger than a first pass: a ~1s pulse/twinkle read as a
 * single flash rather than a glow, gone before it registered as "shiny"
 * rather than "something blinked." Everything here runs on a multi-second
 * cycle instead, and a small warm/cool palette (gold, ember orange, teal)
 * replaces a single flat tint so the sparkle field itself reads as
 * glittering dust rather than one colour of blinking dot.
 *
 * Draws inside whatever box [modifier] sizes — pass a size noticeably
 * larger than the marker itself (see call sites) so the glow and sparkles
 * have room to extend past its edge instead of sitting flush against it,
 * and centre this Canvas and the marker on the same point.
 */
@Composable
fun CurrentPositionGlow(modifier: Modifier = Modifier, sparkleCount: Int = 9) {
    val transition = rememberInfiniteTransition(label = "current-position-glow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "current-position-pulse"
    )
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "current-position-orbit"
    )
    val twinklePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "current-position-twinkle"
    )
    Canvas(modifier = modifier) {
        val halfSize = size.minDimension / 2f
        // The halo sits mostly UNDER the marker (radius well below halfSize)
        // rather than ringing its outside — the sparkles orbiting further
        // out are what read as "light scattering off this spot"; the halo
        // is just what makes the marker itself look lit from within.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GoldAccent.copy(alpha = 0.18f + 0.24f * pulse), Color.Transparent)
            ),
            radius = halfSize * (0.68f + 0.14f * pulse)
        )
        drawGlowSparkles(
            count = sparkleCount,
            halfSize = halfSize,
            baseAngleDeg = orbitAngle,
            twinklePhaseDeg = twinklePhase,
            palette = GlowPalette
        )
    }
}

/**
 * Gold, ember orange, teal — three colours already load-bearing elsewhere
 * in the theme (medal gold, the primary accent, the secondary accent), so
 * the sparkle field reads as "this app's own sparkle" rather than a
 * generic rainbow effect dropped on top of it.
 */
private val GlowPalette = listOf(GoldAccent, Orange, Teal)

private const val SPARKLE_MIN_RADIUS_FRACTION = 0.55f
private const val SPARKLE_MAX_RADIUS_FRACTION = 1.02f
private const val SPARKLE_GLYPH_FRACTION = 0.058f

/**
 * Same shape as LevelAvatar.kt's frame sparkles (deterministic per-particle
 * jitter, integer twinkle speeds so each one stays seamless across its
 * infiniteRepeatable's Restart wrap) — kept as a separate, smaller copy here
 * rather than shared, since this one cycles through [palette] per particle
 * and is sized off a whole map node/row rather than a profile badge.
 */
private fun DrawScope.drawGlowSparkles(
    count: Int,
    halfSize: Float,
    baseAngleDeg: Float,
    twinklePhaseDeg: Float,
    palette: List<Color>
) {
    val origin = center
    repeat(count) { i ->
        val radiusJitter = ((i * 53) % 100) / 100f
        val sizeJitter = ((i * 29) % 100) / 100f
        val brightnessCap = 0.65f + ((i * 71) % 100) / 100f * 0.35f
        val tint = palette[i % palette.size]

        val particleRadius = halfSize * (SPARKLE_MIN_RADIUS_FRACTION + radiusJitter * (SPARKLE_MAX_RADIUS_FRACTION - SPARKLE_MIN_RADIUS_FRACTION))
        val angleRad = Math.toRadians((baseAngleDeg + i * (360f / count)).toDouble())
        val point = Offset(
            origin.x + particleRadius * cos(angleRad).toFloat(),
            origin.y + particleRadius * sin(angleRad).toFloat()
        )

        // 1 + (i % 3) rather than % 4: a slower ceiling keeps even the
        // fastest-twinkling particle on a multi-second cycle now that
        // twinklePhaseDeg's own period was stretched to 2.6s — a first
        // pass's %4 speed on a 1s period is what made this read as a
        // flash instead of a shimmer.
        val twinkleSpeed = 1 + (i % 3)
        val twinkleRad = Math.toRadians((twinklePhaseDeg * twinkleSpeed + i * 61).toDouble())
        val twinkle = (sin(twinkleRad).toFloat() + 1f) / 2f
        val dotRadius = halfSize * SPARKLE_GLYPH_FRACTION * (0.55f + sizeJitter * 0.8f) * (0.5f + twinkle * 0.7f)
        if (dotRadius <= 0f) return@repeat
        val alpha = twinkle * brightnessCap
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = alpha * 0.9f), tint.copy(alpha = 0f)),
                center = point,
                radius = dotRadius * 2.6f
            ),
            radius = dotRadius * 2.6f,
            center = point
        )
        // A white core rather than the palette colour at full strength —
        // reads as a bright glint with the palette colour as its halo,
        // the same two-layer look real light scatter has, instead of a
        // flat coloured disc.
        drawCircle(color = Color.White.copy(alpha = alpha * 0.85f), radius = dotRadius * 0.5f, center = point)
    }
}
