package com.sualtikasifi.cizimhafiza.presentation.game

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R

/**
 * Dispatches to the right screen for the current [GamePhase]. Drawing/Break/
 * Guess/Result share one [GameViewModel] instance (scoped to this NavHost
 * destination) since they're really one continuous timed session — see the
 * comment on [com.sualtikasifi.cizimhafiza.presentation.navigation.Screen].
 *
 * [AnimatedContent]'s `contentKey` is set to the phase's class rather than
 * the phase value itself: [GamePhase.Drawing] changes every second as the
 * countdown ticks, and animating a full crossfade on every tick would
 * flicker — we only want to fade when the phase *kind* changes (e.g.
 * Drawing → Break), not on every field update within the same phase.
 */
@Composable
fun GameScreen(
    onMainMenu: () -> Unit,
    // Level-map mode only: lets the Result screen offer a "Sonraki Bölüm"
    // action next to the usual "Tekrar Oyna"/"Ana Menü" pair. null in free play.
    onLevelNextAction: (() -> Unit)? = null,
    nextActionLabel: String? = null,
    viewModel: GameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    var showExitConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Shown once the Result screen is already on-screen (never blocks the
    // transition into it) — see AdManager's placement doc.
    LaunchedEffect(phase is GamePhase.Result) {
        if (phase is GamePhase.Result) {
            (context as? Activity)?.let { viewModel.showResultInterstitial(it) }
        }
    }

    // Only guard against accidental back-press while a round is actually in
    // progress — Loading is instantaneous and Result has nothing left to lose.
    val isActivelyPlaying = phase is GamePhase.Drawing || phase is GamePhase.Break || phase is GamePhase.Guessing
    BackHandler(enabled = isActivelyPlaying) { showExitConfirm = true }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.exit_game_title)) },
            text = { Text(stringResource(R.string.exit_game_message)) },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; onMainMenu() }) {
                    Text(stringResource(R.string.exit_game_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.exit_game_cancel))
                }
            }
        )
    }

    AnimatedContent(
        targetState = phase,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(200)) },
        label = "game-phase"
    ) { current ->
        when (current) {
            is GamePhase.Loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is GamePhase.Drawing -> DrawingScreen(
                state = current,
                onStrokeFinished = viewModel::onStrokeFinished,
                onStrokeProgress = viewModel::onStrokeProgress,
                onClearCanvas = viewModel::onClearCanvas,
                onEraseStroke = viewModel::onEraseStroke,
                onUndoLastStroke = viewModel::onUndoLastStroke,
                onNextWord = viewModel::advanceRelaxedDrawing,
                onBackClick = { showExitConfirm = true }
            )

            is GamePhase.Break -> BreakScreen(state = current)

            is GamePhase.Guessing -> GuessScreen(
                state = current,
                onSubmit = viewModel::submitGuess,
                onAnswerChanged = viewModel::onAnswerChanged,
                onHintClick = { (context as? Activity)?.let { viewModel.useHint(it) } }
            )

            is GamePhase.Result -> ResultScreen(
                state = current,
                onPlayAgain = viewModel::restart,
                onMainMenu = onMainMenu,
                onLevelNextAction = onLevelNextAction,
                nextActionLabel = nextActionLabel
            )
        }
    }
}
