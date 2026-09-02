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
import com.sualtikasifi.cizimhafiza.presentation.theme.DisplayFont
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
                animationSpec = infiniteRepeatable(tween(SPARKLE_DRIFT_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "level-frame-sparkle-orbit"
            )
            val twinklePhase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(SPARKLE_TWINKLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "level-frame-sparkle-twinkle"
            )
            // Drawn on top of the ring, and not clipped to this Box's own
            // bounds, so the sparkles are free to scatter past the ring
            // image's own edge rather than being squeezed inside it.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSparkles(
                    count = sparkleCount,
                    halfSize = this.size.minDimension / 2f,
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
            // Read outside the Canvas lambda: DrawScope is not a composable
            // scope, so a MaterialTheme lookup inside it will not compile.
            val faceGlow = MaterialTheme.colorScheme.surface
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowRadius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            faceGlow.copy(alpha = 0.92f),
                            faceGlow.copy(alpha = 0.78f),
                            faceGlow.copy(alpha = 0f)
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
                color = MaterialTheme.colorScheme.onSurface,
                // Baloo 2, not the inherited body copy font: this number is
                // the same kind of thing as a score or a timer (see
                // theme/Type.kt's own reasoning for DisplayFont) — this face
                // was rendering it in Quicksand Medium (LocalTextStyle.current
                // defaults to MaterialTheme.typography.bodyLarge, the *body*
                // font), which read as plain, generic UI text rather than
                // matching the bold, rounded numerals the rest of the game
                // uses for anything worth celebrating.
                fontFamily = DisplayFont,
                fontWeight = FontWeight.ExtraBold,
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
 * How many drifting sparkles a frame gets, by [AvatarFrame.unlockLevel] — 0
 * for the first 7 of [AvatarFrame]'s 11 frames, then climbing for the last 4
 * so the glitter itself reads as a rarity tell, not just an on/off flag.
 * Deliberately a lot more than a first pass: a handful of icon-sized glints
 * read as a few stray plus-signs; this many tiny pinpoints reads as dust.
 */
private fun sparkleCountFor(unlockLevel: Int): Int = when {
    unlockLevel >= 100 -> 26
    unlockLevel >= 90 -> 20
    unlockLevel >= 80 -> 14
    unlockLevel >= 70 -> 9
    else -> 0
}

/**
 * The sparkle band's distance from the badge's own centre, as a fraction of
 * its half-size (1.0 = the ring's own outer edge). Deliberately straddling
 * and exceeding 1.0 — a band that stayed under 1.0 kept every sparkle
 * sitting on top of the ring artwork itself, which read as decoration on
 * the frame rather than a glint escaping past it.
 */
private const val SPARKLE_MIN_RADIUS_FRACTION = 0.6f
private const val SPARKLE_MAX_RADIUS_FRACTION = 1.65f

/** A sparkle's own dot size, as a fraction of the badge's half-size — tiny pinpoints of light, not icons. */
private const val SPARKLE_GLYPH_FRACTION = 0.05f

private const val SPARKLE_DRIFT_MILLIS = 14_000
private const val SPARKLE_TWINKLE_MILLIS = 1_100

/**
 * A scattering of tiny white pinpoints drifting slowly around the frame, each
 * twinkling (fading and growing) on its own phase and sitting at its own
 * fixed distance from the ring — offset by large, non-round per-index steps
 * so neither the spacing nor the phase ever reads as one mechanical ring of
 * identical icons pulsing in lockstep. The classic "enchanted item" dust,
 * not a handful of spinning plus-signs.
 */
private fun DrawScope.drawSparkles(count: Int, halfSize: Float, baseAngleDeg: Float, twinklePhaseDeg: Float) {
    val origin = this.center
    repeat(count) { i ->
        // Deterministic per-particle jitter (no separate random source to
        // manage across recompositions) — a couple of large odd multipliers
        // per index, wrapped into a 0f..1f fraction each.
        val radiusJitter = ((i * 53) % 100) / 100f
        val sizeJitter = ((i * 29) % 100) / 100f
        val brightnessCap = 0.55f + ((i * 71) % 100) / 100f * 0.45f

        val particleRadius = halfSize * (SPARKLE_MIN_RADIUS_FRACTION + radiusJitter * (SPARKLE_MAX_RADIUS_FRACTION - SPARKLE_MIN_RADIUS_FRACTION))
        val angleRad = Math.toRadians((baseAngleDeg + i * (360f / count)).toDouble())
        val point = Offset(
            origin.x + particleRadius * cos(angleRad).toFloat(),
            origin.y + particleRadius * sin(angleRad).toFloat()
        )

        // twinklePhaseDeg is a shared infiniteRepeatable(RepeatMode.Restart)
        // value that jumps from 360° back to 0° once per cycle. sin() is
        // only continuous across that jump when its argument's coefficient
        // of twinklePhaseDeg is a whole number (sin(k*360 + x) == sin(x)
        // only for integer k) — the earlier ×1.7 broke that, so every
        // sparkle's brightness snapped to a new value in lockstep once a
        // second, which read as the whole ring "refreshing" rather than
        // continuously twinkling. Integer per-particle speeds keep each
        // sparkle perfectly seamless across the wrap while still giving
        // them different twinkle rates.
        val twinkleSpeed = 1 + (i % 4)
        val twinkleRad = Math.toRadians((twinklePhaseDeg * twinkleSpeed + i * 61).toDouble())
        val twinkle = (sin(twinkleRad).toFloat() + 1f) / 2f // 0f..1f, own phase per sparkle
        val dotRadius = halfSize * SPARKLE_GLYPH_FRACTION * (0.5f + sizeJitter * 0.8f) * (0.4f + twinkle * 0.8f)
        drawSparkleDot(center = point, radius = dotRadius, alpha = twinkle * brightnessCap)
    }
}

/** One glint: a small soft white glow with a brighter pinpoint core — reads as a twinkle at any size, unlike a hard-edged shape. */
private fun DrawScope.drawSparkleDot(center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha * 0.9f), Color.White.copy(alpha = 0f)),
            center = center,
            radius = radius * 2.2f
        ),
        radius = radius * 2.2f,
        center = center
    )
    drawCircle(color = Color.White.copy(alpha = alpha), radius = radius * 0.55f, center = center)
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
