package com.sualtikasifi.cizimhafiza.presentation.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.game.DrawingScreen
import com.sualtikasifi.cizimhafiza.presentation.game.GamePhase
import com.sualtikasifi.cizimhafiza.presentation.game.GuessScreen

/**
 * First-launch walkthrough: the real DrawingScreen/GuessScreen composables
 * driven by [TutorialViewModel]'s hardcoded 3-word run, with a full-screen
 * coaching card between turns. Nothing here is scored or saved.
 */
@Composable
fun TutorialScreen(
    onFinished: () -> Unit,
    viewModel: TutorialViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val coach by viewModel.coach.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    LaunchedEffect(isFinished) {
        if (isFinished) {
            viewModel.completeTutorial()
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = phase) {
            is GamePhase.Drawing -> DrawingScreen(
                state = current,
                onStrokeFinished = viewModel::onStrokeFinished,
                onStrokeProgress = viewModel::onStrokeProgress,
                onClearCanvas = viewModel::onClearCanvas,
                onEraseStroke = viewModel::onEraseStroke,
                onUndoLastStroke = viewModel::onUndoLastStroke,
                onNextWord = viewModel::advanceUntimedDrawing,
                // Leaving mid-tutorial just marks it done and drops the
                // player at the main menu — no exit-confirm dialog needed
                // since there's no real progress to lose.
                onBackClick = {
                    viewModel.completeTutorial()
                    onFinished()
                }
            )

            is GamePhase.Guessing -> GuessScreen(
                state = current,
                onSubmit = viewModel::submitGuess,
                onAnswerChanged = viewModel::onAnswerChanged
            )

            else -> Box(
                modifier = Modifier.fillMaxSize().screenBackground(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        coach?.let { message ->
            CoachOverlay(
                coach = message,
                onContinue = viewModel::dismissCoach,
                onSkip = {
                    viewModel.completeTutorial()
                    onFinished()
                }
            )
        }
    }
}

@Composable
private fun CoachOverlay(
    coach: TutorialCoach,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    // Opaque scrim: while a coaching card is up the timer underneath is
    // stopped anyway (see TutorialViewModel), so there's nothing behind it
    // the player needs to keep watching.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        RaisedCard(corner = 28.dp, modifier = Modifier.fillMaxWidth().padding(28.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = coach.emoji, style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(coach.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(coach.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(coach.buttonRes),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(R.string.tutorial_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
