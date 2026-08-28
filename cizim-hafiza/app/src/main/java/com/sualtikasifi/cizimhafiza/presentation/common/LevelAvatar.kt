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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player's identity everywhere they're seen by someone else: their level
 * number inside a ring whose *material* — not just its colour — escalates
 * with [LevelTier].
 *
 * Karalak's own tier names already tell an art-mastery story (Karalamacı →
 * Çırak → Ressam → Usta Ressam → Sanatçı → Büyük Usta), so the ring draws
 * that story instead of a generic game-loot shine: graphite scribble →
 * coloured pencil → a real brush stroke → oil-and-varnish → watercolour
 * bleed → a gilded gallery frame. Each step is a different drawing medium,
 * not just a different palette.
 *
 * Motion is still rationed to [LevelTier.MASTER_PAINTER] and up — a room can
 * show up to [com.sualtikasifi.cizimhafiza.util.GameConstants.MAX_ROOM_SIZE]
 * of these at once, and every animated instance is a running
 * infiniteTransition, so "most players see a static badge" keeps that cost
 * rare rather than per-row.
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
            // A pencil doesn't lay down one perfect line — it's a few
            // overlapping passes that only add up to a circle when you
            // squint. No glow yet: graphite has nothing to shine.
            LevelTier.SCRIBBLER -> ScribbleRing(ringWidth = ringWidth)

            // A handful of coloured-pencil arcs, each its own stroke rather
            // than one smooth gradient — the first hint of colour choice.
            LevelTier.APPRENTICE -> ColoredPencilRing(ringWidth = ringWidth)

            // A real brush stroke: an organic, slightly uneven ring instead
            // of a mathematically perfect circle, with a soft glow now that
            // there's actual paint down.
            LevelTier.PAINTER -> BrushStrokeRing(color = Orange, glow = Orange, ringWidth = ringWidth)

            // First tier with real motion: an oil-painted ring with a
            // varnish shine that slowly travels around it, breathing gently.
            LevelTier.MASTER_PAINTER -> VarnishRing(
                brush = Brush.sweepGradient(listOf(Orange, GoldAccent, Orange, OrangeDeep, Orange)),
                glow = GoldAccent,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                shinePeriodMillis = MASTER_SHINE_MILLIS
            )

            // Watercolour: soft bleeding blobs of colour drifting around the
            // ring instead of a hard sweep, with a few paint-drop particles
            // orbiting just outside it.
            LevelTier.ARTIST -> WatercolorRing(
                colors = listOf(WrongRed, GoldAccent, Orange),
                glow = GoldAccent,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                periodMillis = ARTIST_DRIFT_MILLIS
            )

            // The finished piece, framed: a gilded ring with carved corner
            // notches and a gallery spotlight sweeping slowly across it —
            // motion as presentation, not as an effect.
            LevelTier.GRAND_MASTER -> GildedFrameRing(
                brush = Brush.sweepGradient(listOf(GoldAccent, CardWhite, GoldAccent, OrangeDeep, GoldAccent)),
                glow = GoldAccent,
                ringWidth = ringWidth * GLOW_RING_SCALE,
                spotPeriodMillis = GRAND_MASTER_SPOT_MILLIS
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

// ---------------------------------------------------------------------------
// Karalamacı — graphite scribble
// ---------------------------------------------------------------------------

private val GRAPHITE_DARK = Color(0xFF3A3733)
private val GRAPHITE_LIGHT = Color(0xFF6B6560)

/** A pencil circle drawn as 2-3 overlapping passes at slightly different radii — never one clean line. */
@Composable
private fun ScribbleRing(ringWidth: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f
        drawCircle(
            color = GRAPHITE_LIGHT.copy(alpha = 0.5f),
            radius = radius * 0.96f,
            style = Stroke(width = ringWidth.toPx() * 0.55f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = GRAPHITE_DARK.copy(alpha = 0.85f),
            radius = radius * 1.03f,
            style = Stroke(width = ringWidth.toPx() * 0.7f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = GRAPHITE_DARK.copy(alpha = 0.6f),
            radius = radius,
            style = Stroke(width = ringWidth.toPx() * 0.4f, cap = StrokeCap.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// Çırak — coloured pencil
// ---------------------------------------------------------------------------

private val PENCIL_PALETTE = listOf(
    Color(0xFFD97757), // terracotta red
    Color(0xFFE0B94F), // ochre yellow
    Color(0xFF5B9BD5), // sky blue
    Color(0xFF6FAE72)  // leaf green
)

/** A handful of separate pencil-coloured arcs rather than one blended gradient — each colour its own stroke. */
@Composable
private fun ColoredPencilRing(ringWidth: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f
        val topLeft = Offset(this.size.width / 2f - radius, this.size.height / 2f - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val sweepPer = 360f / PENCIL_PALETTE.size
        val gapDegrees = 14f
        PENCIL_PALETTE.forEachIndexed { i, color ->
            drawArc(
                color = color,
                startAngle = i * sweepPer + gapDegrees / 2f,
                sweepAngle = sweepPer - gapDegrees,
                useCenter = false,
                style = Stroke(width = ringWidth.toPx() * 0.85f, cap = StrokeCap.Round),
                topLeft = topLeft,
                size = arcSize
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ressam — brush stroke
// ---------------------------------------------------------------------------

/**
 * A circle with an organic wobble instead of mathematically perfect
 * roundness — sampled from a couple of fixed sine harmonics (not random),
 * so the wobble is stable frame to frame instead of flickering.
 */
private fun wobblyCirclePath(center: Offset, baseRadius: Float, amplitude: Float): Path {
    val path = Path()
    val steps = 72
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val angle = t * 2f * PI.toFloat()
        val wobble = (sin(angle * 3f) * 0.6f + sin(angle * 7f + 1.3f) * 0.4f) * amplitude
        val r = baseRadius + wobble
        val point = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

/** A real brush stroke: an uneven ring, plus a second thinner pass for bristle texture, plus a soft halo — the first tier with actual paint down. */
@Composable
private fun BrushStrokeRing(color: Color, glow: Color, ringWidth: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawGlow(glow, haloScale = GLOW_HALO_SCALE * 0.9f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = (this.size.minDimension - ringWidth.toPx()) / 2f
        val amplitude = ringWidth.toPx() * 0.22f
        drawPath(
            path = wobblyCirclePath(center, baseRadius, amplitude),
            color = color,
            style = Stroke(width = ringWidth.toPx() * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = wobblyCirclePath(center, baseRadius * 1.01f, amplitude * 0.7f),
            color = color.darken(0.8f).copy(alpha = 0.55f),
            style = Stroke(width = ringWidth.toPx() * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ---------------------------------------------------------------------------
// Usta Ressam — oil + varnish shine
// ---------------------------------------------------------------------------

/** [BrushStrokeRing]'s successor: a full oil-paint sweep, breathing gently, with a bright varnish highlight slowly travelling around it. */
@Composable
private fun VarnishRing(brush: Brush, glow: Color, ringWidth: Dp, shinePeriodMillis: Int) {
    val transition = rememberInfiniteTransition(label = "level-varnish")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + PULSE_AMPLITUDE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level-varnish-pulse"
    )
    val shineAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(shinePeriodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "level-varnish-shine"
    )
    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        drawGlow(glow)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f
        drawCircle(brush = brush, radius = radius, style = Stroke(width = ringWidth.toPx()))
        withTransform({ rotate(degrees = shineAngle, pivot = center) }) {
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = -14f,
                sweepAngle = 28f,
                useCenter = false,
                style = Stroke(width = ringWidth.toPx() * 0.4f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sanatçı — watercolour bleed
// ---------------------------------------------------------------------------

/** Soft, overlapping colour blobs drifting slowly around the ring — bleeding into each other instead of a hard sweep — plus a few drifting, twinkling paint-drop particles. */
@Composable
private fun WatercolorRing(colors: List<Color>, glow: Color, ringWidth: Dp, periodMillis: Int) {
    val transition = rememberInfiniteTransition(label = "level-watercolor")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "level-watercolor-drift"
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + PULSE_AMPLITUDE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level-watercolor-pulse"
    )
    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        drawGlow(glow, haloScale = GLOW_HALO_SCALE * 1.1f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f

        // A faint base ring so the shape still reads clearly in the gaps
        // between blobs, then each colour bleeds onto it from its own
        // drifting position.
        drawCircle(color = colors.first().copy(alpha = 0.22f), radius = radius, style = Stroke(width = ringWidth.toPx() * 0.6f))
        colors.forEachIndexed { i, color ->
            val blobAngle = Math.toRadians((angle + i * (360f / colors.size)).toDouble())
            val point = Offset(
                center.x + radius * cos(blobAngle).toFloat(),
                center.y + radius * sin(blobAngle).toFloat()
            )
            val blobRadius = ringWidth.toPx() * 2.1f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0f)),
                    center = point,
                    radius = blobRadius
                ),
                radius = blobRadius,
                center = point
            )
        }

        // Paint-drop particles, each on its own twinkle phase derived from
        // [angle] rather than a second transition.
        val dropRadius = radius + ringWidth.toPx() * 1.0f
        repeat(WATERCOLOR_DROP_COUNT) { i ->
            val dropAngle = angle * 0.6f + i * (360f / WATERCOLOR_DROP_COUNT)
            val rad = Math.toRadians(dropAngle.toDouble())
            val twinkle = (sin(Math.toRadians((angle * 2.5 + i * 53).toDouble())).toFloat() + 1f) / 2f
            val point = Offset(
                center.x + dropRadius * cos(rad).toFloat(),
                center.y + dropRadius * sin(rad).toFloat()
            )
            drawCircle(
                color = colors[i % colors.size].copy(alpha = 0.4f + twinkle * 0.6f),
                radius = ringWidth.toPx() * (0.16f + twinkle * 0.12f),
                center = point
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Büyük Usta — gilded gallery frame
// ---------------------------------------------------------------------------

/** A gilded ring with small carved-corner notches (static — a frame doesn't move) and a bright gallery-spotlight highlight slowly sweeping across it. */
@Composable
private fun GildedFrameRing(brush: Brush, glow: Color, ringWidth: Dp, spotPeriodMillis: Int) {
    val transition = rememberInfiniteTransition(label = "level-gilded")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f + PULSE_AMPLITUDE,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level-gilded-pulse"
    )
    val spotAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spotPeriodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "level-gilded-spot"
    )
    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        drawGlow(glow, haloScale = GLOW_HALO_SCALE * 1.15f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = (this.size.minDimension - ringWidth.toPx()) / 2f
        drawCircle(brush = brush, radius = radius, style = Stroke(width = ringWidth.toPx()))

        // Small diamond notches evenly spaced — a carved-frame cue, fixed
        // in place rather than orbiting, the way an actual frame's
        // ornamentation doesn't move.
        val notchSize = ringWidth.toPx() * 0.22f
        repeat(GILDED_NOTCH_COUNT) { i ->
            val a = Math.toRadians((i * (360f / GILDED_NOTCH_COUNT)).toDouble())
            val point = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
            withTransform({ rotate(degrees = 45f, pivot = point) }) {
                drawRect(
                    color = CardWhite.copy(alpha = 0.6f),
                    topLeft = Offset(point.x - notchSize / 2f, point.y - notchSize / 2f),
                    size = Size(notchSize, notchSize)
                )
            }
        }

        // The gallery spotlight: broader and brighter than Usta Ressam's
        // varnish shine — this ring isn't just glossy, it's on display.
        withTransform({ rotate(degrees = spotAngle, pivot = center) }) {
            drawArc(
                color = CardWhite.copy(alpha = 0.92f),
                startAngle = -20f,
                sweepAngle = 40f,
                useCenter = false,
                style = Stroke(width = ringWidth.toPx() * 0.55f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers / tuning
// ---------------------------------------------------------------------------

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

private const val RING_WIDTH_FRACTION = 0.09f

/** The animated tiers get a chunkier ring so their motion is actually visible. */
private const val GLOW_RING_SCALE = 1.5f

/** How many ring-widths of the badge the white face gives up on each side. */
private const val FACE_INSET_RINGS = 2.4f

private const val LEVEL_TEXT_FRACTION = 0.40f

/** How far past the ring's own radius the glow halo reaches. */
private const val GLOW_HALO_SCALE = 1.35f

/** How much the badge grows at the top of its breathing-pulse cycle. */
private const val PULSE_AMPLITUDE = 0.08f
private const val PULSE_PERIOD_MILLIS = 1_400

private const val MASTER_SHINE_MILLIS = 3_200
private const val ARTIST_DRIFT_MILLIS = 7_000
private const val GRAND_MASTER_SPOT_MILLIS = 4_200
private const val WATERCOLOR_DROP_COUNT = 4
private const val GILDED_NOTCH_COUNT = 10

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
