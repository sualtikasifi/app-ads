package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

/**
 * The player's identity everywhere they're seen by someone else: their level
 * number inside an illustrated frame whose *material* escalates with
 * [LevelTier].
 *
 * Karalak's own tier names already tell an art-mastery story (Karalamacı →
 * Çırak → Ressam → Usta Ressam → Sanatçı → Büyük Usta), and each tier's frame
 * artwork draws that story directly instead of a generic game-loot shine:
 * graphite scribble → coloured pencils → a watercolour brush → an oil
 * palette knife → a mounted painter's toolkit → a gilded easel frame. Each
 * step is a different drawing medium, not just a different palette (see
 * [TIER_FRAME]).
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
    val frame = TIER_FRAME.getValue(tier)
    val animated = tier.ordinal >= LevelTier.MASTER_PAINTER.ordinal

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (animated) {
            val transition = rememberInfiniteTransition(label = "level-frame-pulse")
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1f + PULSE_AMPLITUDE,
                animationSpec = infiniteRepeatable(
                    animation = tween(PULSE_PERIOD_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "level-frame-pulse-scale"
            )
            Image(
                painter = painterResource(id = frame.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(scale)
            )
        } else {
            Image(
                painter = painterResource(id = frame.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // The face sits inside the frame's own transparent hole, sized off
        // that hole's measured fraction of the artwork (see
        // TierFrame.faceDiameterFraction) so it never overlaps the
        // illustration regardless of how thick or thin that tier's ring is.
        // A faint radial sheen (rather than flat white) and a hairline inset
        // shadow give it a glassy, slightly domed feel instead of a sticker.
        val faceSize = size * frame.faceDiameterFraction
        Box(
            modifier = Modifier
                .size(faceSize)
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
                // Scaled off the face rather than a fixed style so one
                // composable serves both a 28dp list row and a 96dp profile.
                fontSize = (faceSize.value * LEVEL_TEXT_FRACTION).sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** One tier's frame artwork, plus how big that artwork's own transparent hole is (measured from the source PNG, as a fraction of the full badge size) so the level face can be sized to fit it exactly. */
private data class TierFrame(val drawableRes: Int, val faceDiameterFraction: Float)

private val TIER_FRAME = mapOf(
    LevelTier.SCRIBBLER to TierFrame(R.drawable.level_frame_scribbler, 0.70f),
    LevelTier.APPRENTICE to TierFrame(R.drawable.level_frame_apprentice, 0.40f),
    LevelTier.PAINTER to TierFrame(R.drawable.level_frame_painter, 0.54f),
    LevelTier.MASTER_PAINTER to TierFrame(R.drawable.level_frame_master_painter, 0.40f),
    LevelTier.ARTIST to TierFrame(R.drawable.level_frame_artist, 0.49f),
    LevelTier.GRAND_MASTER to TierFrame(R.drawable.level_frame_grand_master, 0.52f)
)

// ---------------------------------------------------------------------------
// Shared helpers / tuning
// ---------------------------------------------------------------------------

private const val LEVEL_TEXT_FRACTION = 0.50f

/** How much the top tiers' badge grows at the top of its breathing-pulse cycle. */
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
