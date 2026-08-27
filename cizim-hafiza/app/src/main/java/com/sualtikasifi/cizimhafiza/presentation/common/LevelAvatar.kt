package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.Orange
import com.sualtikasifi.cizimhafiza.presentation.theme.OrangeDeep
import com.sualtikasifi.cizimhafiza.presentation.theme.Teal
import com.sualtikasifi.cizimhafiza.presentation.theme.TealDeep
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed

/**
 * The player's identity everywhere they're seen by someone else: their level
 * number inside a ring whose material changes with their [LevelTier].
 *
 * There's no uploaded profile picture in Karalak — this badge *is* the
 * avatar, which is what makes a level worth grinding for. The bands are
 * meant to be readable at a glance across a lobby: a flat ring and a
 * rotating gold one shouldn't need a legend to tell apart.
 *
 * Only the top two tiers animate. An animation per row would otherwise mean
 * eight infinite transitions running in a full waiting room, and "rare
 * enough to notice" is the point of the effect anyway.
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
            LevelTier.SCRIBBLER -> StaticRing(
                brush = Brush.linearGradient(listOf(TIER_SCRIBBLER, TIER_SCRIBBLER)),
                ringWidth = ringWidth
            )

            LevelTier.APPRENTICE -> StaticRing(
                brush = Brush.linearGradient(listOf(Teal, TealDeep)),
                ringWidth = ringWidth
            )

            LevelTier.PAINTER -> StaticRing(
                brush = Brush.linearGradient(listOf(Orange, OrangeDeep)),
                ringWidth = ringWidth
            )

            // From here up the ring stops being a flat colour and starts
            // reading as a material — a full sweep around the circle.
            LevelTier.MASTER_PAINTER -> StaticRing(
                brush = Brush.sweepGradient(listOf(Orange, GoldAccent, Orange, OrangeDeep, Orange)),
                ringWidth = ringWidth * GLOW_RING_SCALE
            )

            LevelTier.ARTIST -> RotatingRing(
                brush = Brush.sweepGradient(listOf(WrongRed, GoldAccent, Orange, WrongRed)),
                ringWidth = ringWidth * GLOW_RING_SCALE,
                periodMillis = ARTIST_SPIN_MILLIS
            )

            LevelTier.GRAND_MASTER -> RotatingRing(
                brush = Brush.sweepGradient(listOf(GoldAccent, CardWhite, GoldAccent, OrangeDeep, GoldAccent)),
                ringWidth = ringWidth * GLOW_RING_SCALE,
                periodMillis = GRAND_MASTER_SPIN_MILLIS
            )
        }

        // The face sits inside the ring rather than under it, so the ring's
        // full width always reads as the frame even at list-row sizes.
        Box(
            modifier = Modifier
                .size(size - ringWidth * FACE_INSET_RINGS)
                .clip(CircleShape)
                .background(CardWhite),
            contentAlignment = Alignment.Center
        ) {
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

@Composable
private fun StaticRing(brush: Brush, ringWidth: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = brush,
            radius = (this.size.minDimension - ringWidth.toPx()) / 2f,
            style = Stroke(width = ringWidth.toPx())
        )
    }
}

@Composable
private fun RotatingRing(brush: Brush, ringWidth: Dp, periodMillis: Int) {
    val transition = rememberInfiniteTransition(label = "level-ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "level-ring-angle"
    )
    Canvas(modifier = Modifier.fillMaxSize().rotate(angle)) {
        drawCircle(
            brush = brush,
            radius = (this.size.minDimension - ringWidth.toPx()) / 2f,
            style = Stroke(width = ringWidth.toPx()),
            center = Offset(this.size.width / 2f, this.size.height / 2f)
        )
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
