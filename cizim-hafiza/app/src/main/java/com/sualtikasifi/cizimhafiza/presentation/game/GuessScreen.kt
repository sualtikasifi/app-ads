package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed

@Composable
fun GuessScreen(
    state: GamePhase.Guessing,
    onSubmit: (String) -> Unit
) {
    var answer by remember(state.guessNumber) { mutableStateOf("") }
    val isAnswered = state.feedback != null

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(
                text = "${state.guessNumber}/${state.totalGuesses}",
                style = MaterialTheme.typography.bodyLarge
            )

            StrokeCanvas(
                strokes = state.strokes,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 12.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline)
                    .background(Color.White)
            )

            Text(text = stringResource(R.string.what_did_you_draw), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                enabled = !isAnswered,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )

            Button(
                onClick = { onSubmit(answer) },
                enabled = !isAnswered && answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.submit_guess))
            }

            AnimatedVisibility(visible = isAnswered) {
                val feedback = state.feedback
                if (feedback != null) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = if (feedback.isCorrect) "✓" else "✗",
                            color = if (feedback.isCorrect) CorrectGreen else WrongRed,
                            style = MaterialTheme.typography.headlineLarge
                        )
                        if (!feedback.isCorrect) {
                            Text(text = stringResource(R.string.correct_answer_was, feedback.correctAnswer))
                        }
                    }
                }
            }
        }
    }
}
