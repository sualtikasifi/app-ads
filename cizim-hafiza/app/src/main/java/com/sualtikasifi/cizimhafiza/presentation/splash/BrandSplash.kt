package com.sualtikasifi.cizimhafiza.presentation.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.theme.CreamBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.DisplayFont
import com.sualtikasifi.cizimhafiza.presentation.theme.Orange
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import kotlin.math.PI
import kotlin.math.sin

/**
 * The brand moment: a pencil writes "Karalak" under the mark, underlines it,
 * and lifts away.
 *
 * Why this exists as an app-owned screen rather than as a fancier system
 * splash: the system splash (see Theme.Karalak.Splash) is deliberately
 * limited to one background color and one icon. It can hold an
 * AnimatedVectorDrawable on API 31+ and nothing at all on older versions,
 * it cannot play video, and it is capped at a fraction of a second. Anything
 * with real motion has to live here, in Compose, drawn over the app after
 * the system has handed the window across.
 *
 * The hand-off is the whole trick. This screen's first frame is the system
 * splash's last frame — same cream field, same mark, same size, dead centre
 * — so the 180 ms cross-fade between them has nothing to reveal. Only once
 * the system splash is gone does anything move: the mark rises to make room
 * and the pencil starts writing. The player never sees a seam, just one
 * continuous opening.
 *
 * It is also kept honestly short. A word game gets opened many times a day,
 * and every millisecond here is a millisecond of not playing — so the whole
 * thing is [TOTAL_MILLIS], it plays on cold start only (the caller's
 * rememberSaveable), a tap skips straight to the end, and it is skipped
 * outright when the device has animations turned off.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val animationsDisabled = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val progress = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            onFinished()
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(TOTAL_MILLIS, easing = LinearEasing))
        onFinished()
    }
    // A second animateTo on the same Animatable cancels the first, so the
    // timeline above simply stops where the tap caught it and this one runs
    // out the rest — no flag to check on every frame.
    LaunchedEffect(skipped) {
        if (!skipped) return@LaunchedEffect
        progress.animateTo(1f, tween(SKIP_MILLIS, easing = LinearEasing))
        onFinished()
    }

    val mark = painterResource(R.drawable.splash_mark)
    val wordmark = stringResource(R.string.app_name)
    val measurer = rememberTextMeasurer()
    val wordLayout = remember(wordmark, measurer) {
        measurer.measure(
            AnnotatedString(wordmark),
            TextStyle(
                fontFamily = DisplayFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                letterSpacing = 0.5.sp
            )
        )
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read inside the lambda so the fade re-runs in the draw
                // phase only — the splash never recomposes to disappear.
                alpha = 1f - phase(progress.value * TOTAL_MILLIS, FADE_FROM, TOTAL_MILLIS)
            }
            .background(CreamBackground)
            .pointerInput(Unit) { detectTapGestures { skipped = true } }
    ) {
        val elapsed = progress.value * TOTAL_MILLIS
        val rise = FastOutSlowInEasing.transform(phase(elapsed, RISE_FROM, RISE_TO))
        val ink = FastOutSlowInEasing.transform(phase(elapsed, INK_FROM, INK_TO))
        val rule = phase(elapsed, RULE_FROM, RULE_TO)
        val lift = FastOutSlowInEasing.transform(phase(elapsed, LIFT_FROM, LIFT_TO))

        val centre = Offset(size.width / 2f, size.height / 2f)
        val markSide = MARK_SIDE.toPx() * (1f - MARK_SHRINK * rise)
        val markCentreY = centre.y - MARK_RISE.toPx() * rise

        // Laid out from where the mark ENDS up, not where it is this frame:
        // the writing starts while the mark is still rising, and a baseline
        // that tracked it would drag the wordmark along for those 80 ms.
        val restingMarkBottom = centre.y - MARK_RISE.toPx() +
            MARK_SIDE.toPx() * MARK_ART_FRACTION * (1f - MARK_SHRINK) / 2f
        val wordWidth = wordLayout.size.width.toFloat()
        val wordLeft = centre.x - wordWidth / 2f
        val wordTop = restingMarkBottom + WORD_GAP.toPx()
        val wordBottom = wordTop + wordLayout.size.height

        translate(centre.x - markSide / 2f, markCentreY - markSide / 2f) {
            with(mark) { draw(Size(markSide, markSide)) }
        }

        clipRect(right = wordLeft + wordWidth * ink) {
            drawText(wordLayout, color = TextDark, topLeft = Offset(wordLeft, wordTop))
        }

        // Hand-drawn, so it sags: a ruler-straight line under a wordmark a
        // pencil just wrote would give the trick away.
        //
        // It is drawn on its own, after the pencil has lifted clear, and not
        // traced by the nib — which was the first thing tried. A pencil is
        // held barrel-up, so on the way back along the underline the barrel
        // lies straight across the word that was just written and hides half
        // of it at the exact moment the wordmark is meant to land. The
        // flourish is worth more than the literalism.
        val ruleY = wordBottom - RULE_LIFT.toPx()
        val ruleLeft = wordLeft - RULE_OVERHANG.toPx()
        val ruleRight = wordLeft + wordWidth + RULE_OVERHANG.toPx()
        val rulePath = Path().apply {
            moveTo(ruleLeft, ruleY)
            // Control offset is twice the sag: a quadratic only reaches half
            // its control point's deflection at the midpoint.
            quadraticTo(centre.x, ruleY + RULE_SAG.toPx() * 2f, ruleRight, ruleY)
        }
        clipRect(right = ruleLeft + (ruleRight - ruleLeft) * rule) {
            drawPath(
                rulePath,
                color = Orange,
                style = Stroke(width = RULE_WIDTH.toPx(), cap = StrokeCap.Round)
            )
        }

        val pencilAlpha = phase(elapsed, PENCIL_IN_FROM, PENCIL_IN_TO) * (1f - lift)
        if (pencilAlpha > 0f) {
            val wobble = sin(ink * PI * 5).toFloat() * WOBBLE.toPx()
            val tip = Offset(
                wordLeft + wordWidth * ink + LIFT_X.toPx() * lift,
                wordBottom - NIB_DROP.toPx() + wobble - LIFT_Y.toPx() * lift
            )
            drawGraphiteDust(tip, wordLeft, pencilAlpha)
            drawPencil(tip, PENCIL_LENGTH.toPx(), pencilAlpha)
        }
    }
}

/** 0f before [fromMs], 1f after [toMs], linear in between. */
private fun phase(elapsed: Float, fromMs: Int, toMs: Int): Float =
    ((elapsed - fromMs) / (toMs - fromMs).toFloat()).coerceIn(0f, 1f)

/**
 * Graphite thrown off behind the nib. Deterministic rather than random: a
 * scatter that redrew itself every frame would shimmer, and this one has to
 * sit still on the page it was just laid down on.
 */
private fun DrawScope.drawGraphiteDust(tip: Offset, wordLeft: Float, alpha: Float) {
    repeat(7) { i ->
        val behind = tip.x - (i + 1) * DUST_SPACING.toPx()
        if (behind <= wordLeft) return
        val jitter = ((i * 37) % 13 - 6) * DUST_JITTER.toPx()
        drawCircle(
            color = Graphite,
            radius = (DUST_RADIUS.toPx() - i * DUST_SHRINK.toPx()).coerceAtLeast(0.4f),
            center = Offset(behind, tip.y + jitter),
            alpha = alpha * 0.45f * (1f - i / 7f)
        )
    }
}

/**
 * The pencil itself, built nib-first: everything below is laid out along +x
 * from the nib at the origin and then rotated about it, so the point stays
 * welded to the letter being written no matter what angle the barrel is at.
 */
private fun DrawScope.drawPencil(tip: Offset, length: Float, alpha: Float) {
    val w = length * 0.155f
    rotate(degrees = -34f, pivot = tip) {
        translate(tip.x, tip.y) {
            val nib = Path().apply {
                moveTo(0f, 0f)
                lineTo(length * 0.085f, -w * 0.34f)
                lineTo(length * 0.085f, w * 0.34f)
                close()
            }
            drawPath(nib, Graphite, alpha = alpha)

            val shoulder = Path().apply {
                moveTo(length * 0.085f, -w * 0.34f)
                lineTo(length * 0.24f, -w * 0.5f)
                lineTo(length * 0.24f, w * 0.5f)
                lineTo(length * 0.085f, w * 0.34f)
                close()
            }
            drawPath(shoulder, PencilWoodLight, alpha = alpha)

            drawRect(
                color = PencilWood,
                topLeft = Offset(length * 0.24f, -w / 2f),
                size = Size(length * 0.55f, w),
                alpha = alpha
            )
            // One darker facet along the underside — a flat rectangle reads
            // as a stick, a shaded one reads as a hexagonal pencil.
            drawRect(
                color = PencilWoodShade,
                topLeft = Offset(length * 0.24f, w * 0.16f),
                size = Size(length * 0.55f, w * 0.34f),
                alpha = alpha
            )
            drawRect(
                color = PencilFerrule,
                topLeft = Offset(length * 0.79f, -w / 2f),
                size = Size(length * 0.1f, w),
                alpha = alpha
            )
            drawRect(
                color = PencilEraser,
                topLeft = Offset(length * 0.89f, -w / 2f),
                size = Size(length * 0.11f, w),
                alpha = alpha
            )
        }
    }
}

private val Graphite = Color(0xFF2A2622)
private val PencilWood = Color(0xFFE7B14F)
private val PencilWoodLight = Color(0xFFF2D9A8)
private val PencilWoodShade = Color(0xFFC98F32)
private val PencilFerrule = Color(0xFFBFC4C9)
private val PencilEraser = Color(0xFFE58C7A)

// One cold-start second and a bit, spent as: hold for the hand-off, rise,
// write, underline, lift, leave.
private const val TOTAL_MILLIS = 1160
private const val SKIP_MILLIS = 170
private const val RISE_FROM = 180
private const val RISE_TO = 380
private const val PENCIL_IN_FROM = 240
private const val PENCIL_IN_TO = 330
private const val INK_FROM = 300
private const val INK_TO = 720
private const val RULE_FROM = 780
private const val RULE_TO = 950
private const val LIFT_FROM = 725
private const val LIFT_TO = 870
private const val FADE_FROM = 980

/**
 * The frame the mark is drawn into, NOT the mark's visible size. It matches
 * the 288dp canvas the platform gives a splash icon that has no background
 * of its own (androidx's splashscreen_icon_size_no_background), because
 * @drawable/splash_mark is drawn into exactly that canvas one frame earlier
 * — same asset, same frame, same place, so the cross-fade between the two
 * screens has nothing to reveal. [MARK_ART_FRACTION] is how much of that
 * frame the artwork actually fills (see the asset's own framing), which is
 * what everything laid out below the mark has to measure against.
 */
private val MARK_SIDE = 288.dp
private const val MARK_ART_FRACTION = 0.52f
private const val MARK_SHRINK = 0.1f
private val MARK_RISE = 50.dp
private val WORD_GAP = 22.dp
private val RULE_LIFT = 4.dp
private val RULE_SAG = 3.5.dp
private val RULE_OVERHANG = 10.dp
private val RULE_WIDTH = 3.5.dp
private val NIB_DROP = 8.dp
private val WOBBLE = 1.6.dp
private val LIFT_X = 30.dp
private val LIFT_Y = 40.dp
private val PENCIL_LENGTH = 84.dp
private val DUST_SPACING = 5.dp
private val DUST_JITTER = 0.5.dp
private val DUST_RADIUS = 1.7.dp
private val DUST_SHRINK = 0.16.dp
