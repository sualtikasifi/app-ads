package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.game.BreakScreen
import com.sualtikasifi.cizimhafiza.presentation.game.DrawingScreen
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessScreen

/**
 * Same Drawing→Break→Guessing screens as the offline single-player game
 * (reused as-is) — only the terminal phase differs: instead of the local
 * [com.sualtikasifi.cizimhafiza.presentation.game.ResultScreen], a finished
 * online match currently waits for the opponent and returns to the main
 * menu. A proper side-by-side comparison + rematch screen is the next step.
 */
@Composable
fun OnlineGameScreen(
    onMainMenu: () -> Unit,
    viewModel: OnlineGameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val opponentFinished by viewModel.opponentFinished.collectAsState()

    AnimatedContent(
        targetState = phase,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(200)) },
        label = "online-game-phase"
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
                onNextWord = {} // online matches are always timed — no manual-advance mode
            )

            is GamePhase.Break -> BreakScreen(state = current)

            is GamePhase.Guessing -> GuessScreen(
                state = current,
                onSubmit = viewModel::submitGuess,
                onAnswerChanged = viewModel::onAnswerChanged
            )

            is GamePhase.Result -> WaitingForOpponentScreen(
                totalScore = current.totalScore,
                opponentFinished = opponentFinished,
                onMainMenu = onMainMenu
            )
        }
    }
}

@Composable
private fun WaitingForOpponentScreen(
    totalScore: Int,
    opponentFinished: Boolean,
    onMainMenu: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.online_you_finished, totalScore),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (opponentFinished) {
                Text(
                    text = stringResource(R.string.online_opponent_finished),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(
                    text = stringResource(R.string.main_menu),
                    onClick = onMainMenu,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.online_waiting_for_opponent_result),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
