package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

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
    viewModel: GameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()

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
                onClearCanvas = viewModel::onClearCanvas,
                onNextWord = viewModel::advanceRelaxedDrawing
            )

            is GamePhase.Break -> BreakScreen(state = current)

            is GamePhase.Guessing -> GuessScreen(state = current, onSubmit = viewModel::submitGuess)

            is GamePhase.Result -> ResultScreen(
                state = current,
                onPlayAgain = viewModel::restart,
                onMainMenu = onMainMenu
            )
        }
    }
}
