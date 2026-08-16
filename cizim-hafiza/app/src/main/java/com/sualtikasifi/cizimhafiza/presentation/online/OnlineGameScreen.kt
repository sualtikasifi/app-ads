package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.presentation.game.BreakScreen
import com.sualtikasifi.cizimhafiza.presentation.game.DrawingScreen
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessScreen

/**
 * Same Drawing→Break→Guessing screens as the offline single-player game
 * (reused as-is). Once this player finishes (phase becomes Result), control
 * hands off to [OnlineResultScreen] via [onFinished] — that screen owns
 * waiting for the opponent and the side-by-side comparison.
 */
@Composable
fun OnlineGameScreen(
    onFinished: (roomCode: String) -> Unit,
    viewModel: OnlineGameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()

    LaunchedEffect(phase) {
        if (phase is GamePhase.Result) {
            onFinished(viewModel.roomCode)
        }
    }

    AnimatedContent(
        targetState = phase,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(200)) },
        label = "online-game-phase"
    ) { current ->
        when (current) {
            is GamePhase.Loading, is GamePhase.Result -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is GamePhase.Drawing -> DrawingScreen(
                state = current,
                onStrokeFinished = viewModel::onStrokeFinished,
                onStrokeProgress = viewModel::onStrokeProgress,
                onClearCanvas = viewModel::onClearCanvas,
                onNextWord = {} // online matches are always timed — no manual-advance mode
            )

            is GamePhase.Break -> BreakScreen(state = current)

            is GamePhase.Guessing -> GuessScreen(
                state = current,
                onSubmit = viewModel::submitGuess,
                onAnswerChanged = viewModel::onAnswerChanged
            )
        }
    }
}
