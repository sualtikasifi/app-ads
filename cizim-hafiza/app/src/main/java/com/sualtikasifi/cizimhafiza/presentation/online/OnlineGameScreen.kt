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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
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
    viewModel: OnlineGameViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val startCountdown by viewModel.startCountdown.collectAsState()

    LaunchedEffect(phase) {
        if (phase is GamePhase.Result) {
            onFinished(viewModel.roomCode)
        }
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

@Composable
private fun StartingCountdownScreen(secondsLeft: Int, totalSeconds: Int) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .dotGridBackground(dotColor = MaterialTheme.colorScheme.outline)
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
