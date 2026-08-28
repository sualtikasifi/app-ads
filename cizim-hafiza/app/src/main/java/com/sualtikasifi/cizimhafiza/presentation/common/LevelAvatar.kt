package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.GoldAccent
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player's identity everywhere they're seen by someone else: their level
 * number inside a [frame] ring the player has unlocked and chosen to wear
 * (see [AvatarFrame] — a separate, player-*selectable* ladder from
 * [com.sualtikasifi.cizimhafiza.domain.model.LevelTier], which still drives
 * the fixed rank name text on StatisticsScreen).
 *
 * Motion is rationed to the last few frames in [AvatarFrame] — a room can
 * show up to [com.sualtikasifi.cizimhafiza.util.GameConstants.MAX_ROOM_SIZE]
 * of these at once, and every animated instance is a running
 * infiniteTransition, so "most players see a static badge" keeps that cost
 * rare rather than per-row. Those top frames don't move themselves (a
 * breathing zoom on the ring artwork just read as the icon glitching in a
 * list); instead a handful of small sparkles orbit and twinkle just outside
 * the ring — more of them the higher the frame's [AvatarFrame.unlockLevel] —
 * the same "the fancier the item, the more it glitters" cue as an MMO's
 * enchanted-gear glow, scaled to how rare the frame actually is.
 */
@Composable
fun LevelAvatar(
    level: Int,
    frame: AvatarFrame,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val sparkleCount = sparkleCountFor(frame.unlockLevel)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = frame.drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        if (sparkleCount > 0) {
            val transition = rememberInfiniteTransition(label = "level-frame-sparkle")
            val orbitAngle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(SPARKLE_ORBIT_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "level-frame-sparkle-orbit"
            )
            val twinklePhase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(SPARKLE_TWINKLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "level-frame-sparkle-twinkle"
            )
            // Drawn on top of the ring, and not clipped to this Box's own
            // bounds, so the sparkles are free to glint just outside the
            // ring image itself rather than being squeezed inside it.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSparkles(
                    count = sparkleCount,
                    orbitRadius = this.size.minDimension / 2f * SPARKLE_ORBIT_FRACTION,
                    baseAngleDeg = orbitAngle,
                    twinklePhaseDeg = twinklePhase
                )
            }
        }

        // The number sits in the frame's own transparent hole, sized and
        // positioned off that hole's measured geometry (see
        // AvatarFrame.faceDiameterFraction / faceOffset*Fraction) so it never
        // overlaps the illustration regardless of how thick, thin or
        // off-centre that frame's ring is.
        //
        // Deliberately NOT an opaque disc with a hairline ring: every surface
        // this badge lands on is already light (the cream page, a white card),
        // so a flat grey circle with a hard edge read as a sticker pasted over
        // the artwork rather than as part of it. What's left is an edgeless
        // white glow that is all but invisible against those surfaces while
        // still muting the page's dot texture directly behind the digits and
        // keeping them legible against a busy or dark inner ring.
        val faceSize = size * frame.faceDiameterFraction
        Box(
            modifier = Modifier
                .offset(x = size * frame.faceOffsetXFraction, y = size * frame.faceOffsetYFraction)
                .size(faceSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowRadius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CardWhite.copy(alpha = 0.92f),
                            CardWhite.copy(alpha = 0.78f),
                            CardWhite.copy(alpha = 0f)
                        ),
                        center = this.center,
                        radius = glowRadius
                    ),
                    radius = glowRadius
                )
            }
            // Scaled off the face rather than a fixed style so one composable
            // serves both a 36dp match-chrome badge and an 88dp profile — and
            // three digits ("100") get a smaller fraction so they still clear
            // the circle's narrowing edges.
            val digits = level.toString()
            val fontSize = (faceSize.value * if (digits.length >= 3) LEVEL_TEXT_FRACTION_WIDE else LEVEL_TEXT_FRACTION).sp
            Text(
                text = digits,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                // Deliberately NOT a MaterialTheme.typography style: those pin
                // a fixed lineHeight (titleMedium is 17sp text in a 24sp line —
                // see theme/Type.kt) that does NOT scale with an overridden
                // fontSize. On a small badge that left an ~11sp glyph inside a
                // 24sp line box taller than the face circle itself, which is
                // what pushed the number visibly low. Pinning lineHeight to the
                // font size keeps the line box smaller than the face; the rest
                // is the theme's own centring idiom (see Type.kt's Tight /
                // CenteredLines), applied here at this composable's own scale.
                lineHeight = fontSize,
                style = LocalTextStyle.current.copy(
                    letterSpacing = 0.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared helpers / tuning
// ---------------------------------------------------------------------------

private const val LEVEL_TEXT_FRACTION = 0.58f

/** Three digits ("100") need a smaller share of the face than one or two. */
private const val LEVEL_TEXT_FRACTION_WIDE = 0.42f

/**
 * How many orbiting sparkles a frame gets, by [AvatarFrame.unlockLevel] — 0
 * for the first 7 of [AvatarFrame]'s 11 frames, then climbing for the last 4
 * so the glitter itself reads as a rarity tell, not just an on/off flag.
 */
private fun sparkleCountFor(unlockLevel: Int): Int = when {
    unlockLevel >= 100 -> 8
    unlockLevel >= 90 -> 6
    unlockLevel >= 80 -> 4
    unlockLevel >= 70 -> 3
    else -> 0
}

/** How far outside the ring's own edge the sparkles orbit, as a fraction of the badge's radius. */
private const val SPARKLE_ORBIT_FRACTION = 0.72f

/** A sparkle's own glyph size, as a fraction of [SPARKLE_ORBIT_FRACTION]'s radius. */
private const val SPARKLE_GLYPH_FRACTION = 0.13f

private const val SPARKLE_ORBIT_MILLIS = 9_000
private const val SPARKLE_TWINKLE_MILLIS = 1_100

/**
 * A ring of small four-point glints orbiting just outside the frame, each
 * twinkling (fading and growing) on its own phase — offset by a large,
 * non-round step per index so neighbouring sparkles don't visibly sync up —
 * rather than all pulsing in lockstep.
 */
private fun DrawScope.drawSparkles(count: Int, orbitRadius: Float, baseAngleDeg: Float, twinklePhaseDeg: Float) {
    val origin = this.center
    repeat(count) { i ->
        val orbitRad = Math.toRadians((baseAngleDeg + i * (360f / count)).toDouble())
        val point = Offset(
            origin.x + orbitRadius * cos(orbitRad).toFloat(),
            origin.y + orbitRadius * sin(orbitRad).toFloat()
        )
        val twinkleRad = Math.toRadians((twinklePhaseDeg * 1.7 + i * 61).toDouble())
        val twinkle = (sin(twinkleRad).toFloat() + 1f) / 2f // 0f..1f, own phase per sparkle
        val glyphRadius = orbitRadius * SPARKLE_GLYPH_FRACTION * (0.5f + twinkle * 0.6f)
        drawSparkleGlyph(center = point, radius = glyphRadius, color = GoldAccent.copy(alpha = 0.3f + twinkle * 0.7f))
    }
}

/** One glint: a small diamond with a thin crosshair through it — the classic star-twinkle shape. */
private fun DrawScope.drawSparkleGlyph(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val diamond = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.42f, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.42f, center.y)
        close()
    }
    drawPath(diamond, color = color)
    val armLength = radius * 1.7f
    val armWidth = radius * 0.22f
    drawLine(color, Offset(center.x - armLength, center.y), Offset(center.x + armLength, center.y), strokeWidth = armWidth, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - armLength), Offset(center.x, center.y + armLength), strokeWidth = armWidth, cap = StrokeCap.Round)
}

/**
 * Compact level badge + progress sliver for a match's chrome row — the same
 * ladder as [LevelAvatar]/StatisticsScreen's big card, small enough to sit
 * next to a word counter or a countdown ring without crowding it.
 *
 * Reads live off a [LevelProgressState] StateFlow that updates the instant
 * XP is granted (see GameViewModel/OnlineGameViewModel.levelProgress), so
 * the sliver visibly advances on every correct word instead of only
 * reflecting where the player stood when the match started. [frame] is the
 * player's own chosen ring (see GameViewModel/OnlineGameViewModel.selectedFrame),
 * not a level-derived one, so it matches what they picked on StatisticsScreen.
 */
@Composable
fun LiveLevelBadge(progress: LevelProgressState, frame: AvatarFrame, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LevelAvatar(level = progress.level, frame = frame, size = 36.dp)
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
