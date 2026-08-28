package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.Orange
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeDeep
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal
import com.sualtikasifi.cizimhafiza.presentation.theme.TealDeep
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player's identity everywhere they're seen by someone else: their level
 * number inside a ring whose material — and now motion — escalates with
 * [LevelTier].
 *
 * There's no uploaded profile picture in Karalak — this badge *is* the
 * avatar, which is what makes a level worth grinding for. Each band is a
 * strictly bigger visual promise than the last: a flat ring gives way to a
 * soft glow, then a bevelled shine, then genuine motion (rotation, a
 * breathing pulse, orbiting sparkles) — so the jump from "just hit level 60"
 * to "level 80" reads as a real upgrade even glanced at across a lobby.
 *
 * Motion is deliberately rationed: only [LevelTier.MASTER_PAINTER] and up
 * animate at all, and only [LevelTier.ARTIST]/[LevelTier.GRAND_MASTER] get
 * the full rotate+sparkle treatment. A room can show up to
 * [com.sualtikasifi.cizimhafiza.util.GameConstants.MAX_ROOM_SIZE] of these
 * at once — every animated instance is a running infiniteTransition, so
 * "most players see a static badge" keeps that cost rare rather than
 * per-row.
 */
@Composable
fun LevelAvatar(
    level: Int,
    tier: LevelTier,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val ringWidth = size * RING_WIDTH_FRACTION

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        when (tier) {
            // Flat colour, but no longer a flat DRAW: a soft radial halo
            // behind it plus a glossy highlight arc on top read as a real
            // enamel badge instead of a single stroked circle.
            LevelTier.SCRIBBLER -> BevelledRing(
                brush = Brush.linearGradient(listOf(TIER_SCRIBBLER, TIER_SCRIBBLER.darken())),
                glow = null,
                ringWidth = ringWidth,
                size = size
            )

            LevelTier.APPRENTICE -> BevelledRing(
                brush = Brush.linearGradient(listOf(Teal, TealDeep)),
                glow = Teal,
                ringWidth = ringWidth,
                size = size
            )

            LevelTier.PAINTER -> BevelledRing(
                brush = Brush.linearGradient(listOf(Orange, OrangeDeep)),
                glow = Orange,
                ringWidth = ringWidth,
                size = size
            )

            // First tier with real motion: a slow breathing pulse on top of
            // the existing sweep + glow, no rotation/sparkles yet — those
            // stay reserved for the top two bands.
            LevelTier.MASTER_PAINTER -> PulsingRing(
                brush = Brush.sweepGradient(listOf(Orange, GoldAccent, Orange, OrangeDeep, Orange)),
                glow = GoldAccent,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                size = size
            )

            LevelTier.ARTIST -> LivelyRing(
                brush = Brush.sweepGradient(listOf(WrongRed, GoldAccent, Orange, WrongRed)),
                glow = GoldAccent,
                sparkleColor = GoldAccent,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                size = size,
                periodMillis = ARTIST_SPIN_MILLIS,
                sparkleCount = 4
            )

            LevelTier.GRAND_MASTER -> LivelyRing(
                brush = Brush.sweepGradient(listOf(GoldAccent, CardWhite, GoldAccent, OrangeDeep, GoldAccent)),
                glow = GoldAccent,
                sparkleColor = CardWhite,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                size = size,
                periodMillis = GRAND_MASTER_SPIN_MILLIS,
                sparkleCount = 6
            )
        }

        // The face sits inside the ring rather than under it, so the ring's
        // full width always reads as the frame even at list-row sizes. A
        // faint radial sheen (rather than flat white) and a hairline inset
        // shadow give it a glassy, slightly domed feel instead of a sticker.
        Box(
            modifier = Modifier
                .size(size - ringWidth * FACE_INSET_RINGS)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CardWhite, CardWhite.darken(0.94f)),
                        center = Offset(0.3f, 0.25f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = this.size.minDimension / 2f - 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            Text(
                text = level.toString(),
                color = TextDark,
                fontWeight = FontWeight.Bold,
                // Scaled off the badge rather than a fixed style so one
                // composable serves both a 28dp list row and a 96dp profile.
                fontSize = (size.value * LEVEL_TEXT_FRACTION).sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Soft radial halo behind a ring — a portable stand-in for a real blur that works down to this app's minSdk. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlow(color: Color, haloScale: Float = GLOW_HALO_SCALE) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)),
            radius = this.size.minDimension / 2f * haloScale
        ),
        radius = this.size.minDimension / 2f * haloScale
    )
}

/** Static ring: glow (optional) + the ring itself + a glossy highlight arc across the top-left. */
@Composable
private fun BevelledRing(brush: Brush, glow: Color?, ringWidth: Dp, size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        glow?.let { drawGlow(it) }
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f
        drawCircle(brush = brush, radius = radius, style = Stroke(width = ringWidth.toPx()))
        // A bright, thin arc riding the same stroke, offset to the top-left
        // quadrant only — the classic "light hitting an enamel badge" cue
        // that turns a flat ring into something that reads as an object.
        drawArc(
            color = Color.White.copy(alpha = 0.55f),
            startAngle = 200f,
            sweepAngle = 80f,
            useCenter = false,
            style = Stroke(width = ringWidth.toPx() * 0.35f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            topLeft = Offset((this.size.width / 2f) - radius, (this.size.height / 2f) - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        )
    }
}

/** [BevelledRing] with a slow breathing scale — the first tier where the badge visibly moves on its own. */
@Composable
private fun PulsingRing(brush: Brush, glow: Color, ringWidth: Dp, size: Dp) {
    val transition = rememberInfiniteTransition(label = "level-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + PULSE_AMPLITUDE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level-pulse-scale"
    )
    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        drawGlow(glow)
        drawCircle(
            brush = brush,
            radius = (this.size.minDimension - ringWidth.toPx()) / 2f,
            style = Stroke(width = ringWidth.toPx())
        )
    }
}

/**
 * The top-tier treatment: pulse + rotation + a handful of orbiting sparkles
 * that twinkle as they go around. [sparkleCount] and [sparkleColor] are the
 * only things that differ between Artist and Grand Master — Grand Master
 * just gets more of them, in white instead of gold, on top of its own
 * already-brighter sweep gradient.
 */
@Composable
private fun LivelyRing(
    brush: Brush,
    glow: Color,
    sparkleColor: Color,
    ringWidth: Dp,
    size: Dp,
    periodMillis: Int,
    sparkleCount: Int
) {
    val transition = rememberInfiniteTransition(label = "level-lively")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "level-lively-angle"
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + PULSE_AMPLITUDE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level-lively-scale"
    )
    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        drawGlow(glow, haloScale = GLOW_HALO_SCALE * 1.15f)

        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringRadius = (this.size.minDimension - ringWidth.toPx()) / 2f

        withTransform({ rotate(degrees = angle, pivot = center) }) {
            drawCircle(brush = brush, radius = ringRadius, style = Stroke(width = ringWidth.toPx()))
        }

        // Sparkles ride just outside the ring, spaced evenly, each on its
        // own twinkle phase (derived from the same [angle] driver rather
        // than a second animation) so they don't all fade in and out
        // together like one pulsing halo.
        val orbitRadius = ringRadius + ringWidth.toPx() * 0.9f
        repeat(sparkleCount) { i ->
            val sparkleAngle = angle + i * (360f / sparkleCount)
            val rad = Math.toRadians(sparkleAngle.toDouble())
            val twinkle = (sin(Math.toRadians((angle * 3 + i * 67).toDouble())).toFloat() + 1f) / 2f
            val point = Offset(
                x = center.x + orbitRadius * cos(rad).toFloat(),
                y = center.y + orbitRadius * sin(rad).toFloat()
            )
            drawCircle(
                color = sparkleColor.copy(alpha = 0.35f + twinkle * 0.65f),
                radius = ringWidth.toPx() * (0.18f + twinkle * 0.14f),
                center = point
            )
        }
    }
}

private val TIER_SCRIBBLER = Color(0xFFCBBBA0)

private const val RING_WIDTH_FRACTION = 0.09f

/** The "material" tiers get a chunkier ring so the sweep is actually visible. */
private const val GLOW_RING_SCALE = 1.5f

/** How many ring-widths of the badge the white face gives up on each side. */
private const val FACE_INSET_RINGS = 2.4f

private const val LEVEL_TEXT_FRACTION = 0.40f
private const val ARTIST_SPIN_MILLIS = 6_000
private const val GRAND_MASTER_SPIN_MILLIS = 4_000

/** How far past the ring's own radius the glow halo reaches. */
private const val GLOW_HALO_SCALE = 1.35f

/** How much the badge grows at the top of its breathing-pulse cycle. */
private const val PULSE_AMPLITUDE = 0.08f
private const val PULSE_PERIOD_MILLIS = 1_400

/**
 * Compact level badge + progress sliver for a match's chrome row — the same
 * ladder as [LevelAvatar]/StatisticsScreen's big card, small enough to sit
 * next to a word counter or a countdown ring without crowding it.
 *
 * Reads live off a [LevelProgressState] StateFlow that updates the instant
 * XP is granted (see GameViewModel/OnlineGameViewModel.levelProgress), so
 * the sliver visibly advances on every correct word instead of only
 * reflecting where the player stood when the match started.
 */
@Composable
fun LiveLevelBadge(progress: LevelProgressState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LevelAvatar(level = progress.level, tier = progress.tier, size = 28.dp)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.progressFraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
