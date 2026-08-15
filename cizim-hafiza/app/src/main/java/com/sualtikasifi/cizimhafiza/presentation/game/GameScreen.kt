package com.sualtikasifi.cizimhafiza.presentation.game

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
 */
@Composable
fun GameScreen(
    onMainMenu: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()

    when (val current = phase) {
        is GamePhase.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        is GamePhase.Drawing -> DrawingScreen(
            state = current,
            onStrokeFinished = viewModel::onStrokeFinished,
            onClearCanvas = viewModel::onClearCanvas
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
