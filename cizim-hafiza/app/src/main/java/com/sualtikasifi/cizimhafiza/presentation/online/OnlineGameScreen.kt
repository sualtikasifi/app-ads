package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.game.BreakScreen
import com.sualtikasifi.cizimhafiza.presentation.game.DrawingScreen
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessScreen
import com.sualtikasifi.cizimhafiza.util.GameConstants

/**
 * Same Drawing→Break→Guessing screens as the offline single-player game
 * (reused as-is). Once this player finishes (phase becomes Result), control
 * hands off to [OnlineResultScreen] via [onFinished] — that screen owns
 * waiting for the opponent and the side-by-side comparison.
 */
@Composable
fun OnlineGameScreen(
    onFinished: (roomCode: String) -> Unit,
    onExit: () -> Unit,
    viewModel: OnlineGameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val startCountdown by viewModel.startCountdown.collectAsState()
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase is GamePhase.Result) {
            onFinished(viewModel.roomCode)
        }
    }

    val isActivelyPlaying = phase is GamePhase.Drawing || phase is GamePhase.Break || phase is GamePhase.Guessing
    BackHandler(enabled = isActivelyPlaying) { showExitConfirm = true }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.exit_game_title)) },
            text = { Text(stringResource(R.string.exit_game_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    viewModel.leaveRoom()
                    onExit()
                }) {
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

    val secondsLeft = startCountdown
    if (secondsLeft != null) {
        StartingCountdownScreen(secondsLeft = secondsLeft, totalSeconds = GameConstants.ONLINE_START_COUNTDOWN_SECONDS)
        return
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
                onEraseStroke = viewModel::onEraseStroke,
                onUndoLastStroke = viewModel::onUndoLastStroke,
                onNextWord = {}, // online matches are always timed — no manual-advance mode
                onBackClick = { showExitConfirm = true }
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

@Composable
private fun StartingCountdownScreen(secondsLeft: Int, totalSeconds: Int) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.online_game_starting), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))
            CircularCountdown(
                secondsLeft = secondsLeft,
                totalSeconds = totalSeconds,
                modifier = Modifier.size(140.dp)
            )
        }
    }
}
