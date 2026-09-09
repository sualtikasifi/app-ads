package com.sualtikasifi.cizimhafiza.presentation.quickmatch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.GhostRun
import com.sualtikasifi.cizimhafiza.domain.model.GhostRuns
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Finds a stranger's recorded round to play against, shows who it found,
 * and starts the match itself.
 *
 * What the player sees is a name, a level ring and a countdown — never the
 * score. Underneath, the opponent already played these words and is not
 * sitting there waiting; on screen, the two of you are about to start
 * together. That gap is the whole design of the mode, and showing the
 * result of a round that has already happened is what used to give it away.
 */
@Composable
fun QuickMatchScreen(
    onBack: () -> Unit,
    onStart: (GhostRun) -> Unit,
    viewModel: QuickMatchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .screenBackground()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Clears the floating back button (see ScreenTopActions) —
                // same convention every other screen uses, rather than this
                // screen's own inline title row sitting a row lower than
                // everywhere else's.
                Spacer(modifier = Modifier.height(TopActionsClearance))

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (val current = state) {
                        QuickMatchState.Searching -> SearchingBody()
                        is QuickMatchState.Found -> FoundBody(
                            opponent = current.opponent,
                            onStart = { onStart(current.opponent) }
                        )
                        QuickMatchState.Empty -> MessageBody(
                            title = stringResource(R.string.quick_match_empty_title),
                            body = stringResource(R.string.quick_match_empty_body),
                            actionLabel = stringResource(R.string.quick_match_search_again),
                            onAction = viewModel::search
                        )
                        QuickMatchState.Failed -> MessageBody(
                            title = stringResource(R.string.quick_match_failed_title),
                            body = stringResource(R.string.quick_match_failed_body),
                            actionLabel = stringResource(R.string.quick_match_search_again),
                            onAction = viewModel::search
                        )
                    }
                }
            }
            ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/**
 * A pencil visibly sketching a squiggle, endlessly — the drawing-themed
 * stand-in for a bare spinner, since what this app's "opponent" actually
 * did was draw. Built from sampled points rather than a real hand-drawn
 * path: cheap every frame and exactly reproducible, which a spinner also
 * is but a doodle usually isn't.
 */
@Composable
private fun SearchingBody() {
    val transition = rememberInfiniteTransition(label = "quick_match_draw")
    // Draws left to right, pauses briefly at the end, then starts the next
    // squiggle from scratch — a real sketch does not un-draw itself.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SQUIGGLE_DURATION_MS, easing = LinearEasing)),
        label = "quick_match_draw_progress"
    )
    val tipScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(320, easing = LinearEasing), RepeatMode.Reverse),
        label = "quick_match_tip_scale"
    )

    val strokeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 5.dp.toPx() }
    val tipRadiusPx = with(density) { 7.dp.toPx() }

    Canvas(modifier = Modifier.width(200.dp).height(110.dp)) {
        fun pointAt(t: Float): Offset {
            val x = t * size.width
            val y = size.height / 2f + sin(t * SQUIGGLE_CYCLES * (2f * PI.toFloat())) * (size.height * 0.32f)
            return Offset(x, y)
        }

        val fullPath = Path().apply {
            for (i in 0..SQUIGGLE_SAMPLES) {
                val point = pointAt(i / SQUIGGLE_SAMPLES.toFloat())
                if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(fullPath, color = trackColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

        val drawnSamples = (SQUIGGLE_SAMPLES * progress).toInt().coerceIn(0, SQUIGGLE_SAMPLES)
        if (drawnSamples > 0) {
            val drawnPath = Path().apply {
                for (i in 0..drawnSamples) {
                    val point = pointAt(i / SQUIGGLE_SAMPLES.toFloat())
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(drawnPath, color = strokeColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))

            val tip = pointAt(drawnSamples / SQUIGGLE_SAMPLES.toFloat())
            drawCircle(color = strokeColor, radius = tipRadiusPx * tipScale, center = tip)
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.quick_match_searching),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

private const val SQUIGGLE_SAMPLES = 48
private const val SQUIGGLE_CYCLES = 2.4f
private const val SQUIGGLE_DURATION_MS = 1500

/**
 * The moment a match is found, and the few seconds before it starts.
 *
 * Two things are deliberately NOT here. There is no score to beat: knowing
 * the number before drawing a single word turns the round into chasing a
 * target somebody already hit, when the whole appeal is finding out at the
 * end who did better. And there is no start button — the countdown starts
 * itself. Both changes serve the same illusion, which is the one thing that
 * makes a recorded round feel like a match: that the two of you are about
 * to begin at the same moment.
 *
 * Losing the reroll button follows from losing the score. It existed so a
 * player could decline an opponent who looked unbeatable; with nothing to
 * judge, declining is just a slower way of starting.
 */
@Composable
private fun FoundBody(opponent: GhostRun, onStart: () -> Unit) {
    val start by rememberUpdatedState(onStart)
    val progress = remember { Animatable(0f) }
    // Keyed on the run so a genuinely new opponent restarts the countdown,
    // while a recomposition does not.
    LaunchedEffect(opponent.id) {
        // Reset explicitly: the Animatable outlives a change of opponent,
        // and one already sitting at 1f would "finish" instantly and start
        // the match with no countdown at all.
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNTDOWN_MS, easing = LinearEasing))
        start()
    }
    val secondsLeft = ceil((1f - progress.value) * (COUNTDOWN_MS / 1000f)).toInt().coerceAtLeast(1)

    RaisedCard(corner = 26.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.quick_match_opponent_found),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            CountdownRing(progress = progress.value) {
                LevelAvatar(
                    level = opponent.level,
                    // The stored name is only a preference; resolve() is what
                    // decides which ring that level has actually earned.
                    frame = AvatarFrame.resolve(opponent.frameId, opponent.level),
                    size = 82.dp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = opponent.nickname,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.quick_match_starting_in, secondsLeft),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.quick_match_explainer, GhostRuns.RUN_WORD_COUNT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The countdown drawn as a ring closing around the opponent's avatar, so
 * the thing running out is attached to the person you are about to face
 * rather than sitting somewhere else on screen as a bar.
 */
@Composable
private fun CountdownRing(progress: Float, content: @Composable () -> Unit) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val sweepColor = MaterialTheme.colorScheme.primary
    val strokeWidthPx = with(LocalDensity.current) { 5.dp.toPx() }

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_SIZE)) {
            val inset = strokeWidthPx / 2f
            val stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            // Drains clockwise from the top rather than filling: what the
            // player is watching is time left, not progress made.
            drawArc(
                color = sweepColor,
                startAngle = -90f,
                sweepAngle = 360f * (1f - progress),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        content()
    }
}

private val RING_SIZE = 108.dp
private const val COUNTDOWN_MS = 5_000

@Composable
private fun MessageBody(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    PrimaryButton(
        text = actionLabel,
        onClick = onAction,
        icon = Icons.Filled.Refresh,
        modifier = Modifier.fillMaxWidth()
    )
}
