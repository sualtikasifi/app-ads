package com.sualtikasifi.cizimhafiza.presentation.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.CircularCountdown
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.StatPill
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.TimerWarning
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.capitalizeTr

@Composable
fun GuessScreen(
    state: GamePhase.Guessing,
    onSubmit: (String) -> Unit,
    onAnswerChanged: (String) -> Unit = {}
) {
    var answer by remember(state.guessNumber) { mutableStateOf("") }
    val isAnswered = state.feedback != null
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // No scrolling: the drawing canvas is the one flexible (weight(1f))
        // element, so when the keyboard opens (imePadding shrinks the
        // available height) the canvas simply shrinks to make room — the
        // answer field and Gönder/Atla buttons stay fixed-size and always
        // visible instead of being pushed off-screen or requiring a scroll.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill(text = "${state.guessNumber}/${state.totalGuesses}")
                CircularCountdown(
                    secondsLeft = state.secondsLeft,
                    totalSeconds = state.totalSeconds,
                    ringColor = timerColor
                )
            }

            StrokeCanvas(
                strokes = state.strokes,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .padding(vertical = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(CardWhite)
                    .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            )

            Text(text = stringResource(R.string.what_did_you_draw), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = answer,
                onValueChange = { newValue ->
                    answer = newValue
                    onAnswerChanged(newValue)
                },
                enabled = !isAnswered,
                singleLine = true,
                shape = PillShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.skip_guess),
                    onClick = { onSubmit("") },
                    enabled = !isAnswered,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = stringResource(R.string.submit_guess),
                    onClick = { onSubmit(answer) },
                    enabled = !isAnswered && answer.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = isAnswered,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(tween(150)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val feedback = state.feedback
                if (feedback != null) {
                    val shakeOffset = remember(state.guessNumber) { Animatable(0f) }
                    LaunchedEffect(feedback) {
                        if (!feedback.isCorrect) {
                            shakeOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 400
                                    0f at 0
                                    -16f at 50
                                    16f at 100
                                    -12f at 150
                                    12f at 200
                                    -6f at 250
                                    6f at 300
                                    0f at 400
                                }
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = shakeOffset.value.dp)
                            .padding(top = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (feedback.isCorrect) "✓" else "✗",
                            color = if (feedback.isCorrect) CorrectGreen else WrongRed,
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center
                        )
                        if (!feedback.isCorrect) {
                            Text(
                                text = stringResource(R.string.correct_answer_was, feedback.correctAnswer.capitalizeTr()),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
