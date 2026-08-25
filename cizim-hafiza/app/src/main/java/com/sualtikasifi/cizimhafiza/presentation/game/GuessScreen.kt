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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
import com.sualtikasifi.cizimhafiza.presentation.common.hardEdge
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectContainer
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.TimerWarning
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongContainer
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage

@Composable
fun GuessScreen(
    state: GamePhase.Guessing,
    onSubmit: (String) -> Unit,
    onAnswerChanged: (String) -> Unit = {},
    onHintClick: () -> Unit = {}
) {
    val wordLanguage = currentWordLanguage()
    var answer by remember(state.guessNumber) { mutableStateOf("") }
    val isAnswered = state.feedback != null
    val timerColor = if (state.isWarning) TimerWarning else MaterialTheme.colorScheme.primary

    // Guards against a double-tap firing two rewarded-ad loads for the same
    // click — resets per word, though once state.hintUsed flips true the
    // button is gone for the rest of the match anyway.
    var hintRequested by remember(state.guessNumber) { mutableStateOf(false) }

    // Kept focused (and thus the keyboard kept open) across the whole
    // guessing phase. The field's enabled/readOnly state never changes —
    // even toggling readOnly while focused can make the system hide the
    // IME — so edits during the brief feedback window are blocked purely
    // by ignoring onValueChange, and focus+keyboard are explicitly
    // reasserted on every new guess as a safety net.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.guessNumber) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // No scrolling: the drawing canvas is the one flexible (weight(1f))
        // element, so when the keyboard opens (imePadding shrinks the
        // available height) the canvas simply shrinks to make room — the
        // answer field and Gönder/Atla buttons stay fixed-size and always
        // visible instead of being pushed off-screen or requiring a scroll.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatPill(text = "${state.guessNumber} / ${state.totalGuesses}")
                CircularCountdown(
                    secondsLeft = state.secondsLeft,
                    totalSeconds = state.totalSeconds,
                    ringColor = timerColor,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 96.dp)
                    .padding(bottom = AppTheme.tokens.raise)
                    .hardEdge(AppTheme.tokens.edge, AppTheme.tokens.raise, 26.dp)
                    .background(CardWhite, MaterialTheme.shapes.large)
                    .dotGridBackground(AppTheme.tokens.canvasGrid, spacing = 22.dp, radius = 1.2.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            ) {
                StrokeCanvas(strokes = state.strokes, modifier = Modifier.fillMaxSize())

                // Correct/wrong feedback is drawn ON TOP of the canvas rather
                // than appended below it: as a sibling in the Column it added
                // real height, which stole it from the canvas's weight(1f) and
                // made the drawing visibly shrink the instant an answer landed.
                // As an overlay the layout never moves.
                GuessFeedbackOverlay(
                    visible = isAnswered,
                    feedback = state.feedback,
                    guessNumber = state.guessNumber,
                    wordLanguage = wordLanguage,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.what_did_you_draw),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // One rewarded-ad hint per whole match, not per word (see
            // GameViewModel/OnlineGameViewModel.useHint): before it's spent,
            // a CTA to watch an ad for this word's first letter; once spent,
            // either this word's revealed letter (if it was spent here) or
            // nothing at all (spent on an earlier word — stays out of the way).
            if (!isAnswered && !state.hintUsed) {
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    // Countdown is paused (see useHint) the instant this is
                    // tapped, so the label needs to make clear something is
                    // happening — a frozen timer with no other signal would
                    // otherwise look like the screen had just stalled.
                    text = stringResource(
                        if (hintRequested) R.string.loading_hint else R.string.watch_ad_for_hint
                    ),
                    onClick = {
                        if (!hintRequested) {
                            hintRequested = true
                            onHintClick()
                        }
                    },
                    enabled = !hintRequested,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (state.hintLetter != null) {
                Spacer(modifier = Modifier.height(10.dp))
                // Capitalized the same way the word itself is displayed
                // everywhere else — a Turkish "i" has to become "İ", not "I".
                TintedBadge(
                    text = stringResource(
                        R.string.hint_first_letter,
                        state.hintLetter.capitalizeForWordLanguage(wordLanguage)
                    )
                )
            }

            OutlinedTextField(
                value = answer,
                onValueChange = { newValue ->
                    // Ignore edits once answered instead of toggling
                    // enabled/readOnly, so the field never loses focus.
                    if (!isAnswered) {
                        answer = newValue
                        onAnswerChanged(newValue)
                    }
                },
                singleLine = true,
                shape = PillShape,
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .focusRequester(focusRequester)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.skip_guess),
                    onClick = { onSubmit("") },
                    enabled = !isAnswered,
                    height = 52.dp,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = stringResource(R.string.submit_guess),
                    onClick = { onSubmit(answer) },
                    enabled = !isAnswered && answer.isNotBlank(),
                    height = 52.dp,
                    modifier = Modifier.weight(1f)
                )
            }

        }
    }
}

/**
 * The correct/wrong badge shown over the drawing once an answer lands.
 * Extracted into its own composable purely so [AnimatedVisibility] resolves
 * against no implicit receiver — called inline inside the canvas Box it
 * would bind to the enclosing Column's scoped overload instead.
 */
@Composable
private fun GuessFeedbackOverlay(
    visible: Boolean,
    feedback: GuessFeedback?,
    guessNumber: Int,
    wordLanguage: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(tween(150)),
        modifier = modifier
    ) {
        if (feedback != null) {
            val shakeOffset = remember(guessNumber) { Animatable(0f) }
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
                    .offset(x = shakeOffset.value.dp)
                    .background(CardWhite.copy(alpha = 0.92f), MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            if (feedback.isCorrect) CorrectContainer else WrongContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (feedback.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (feedback.isCorrect) CorrectGreen else WrongRed,
                        modifier = Modifier.size(34.dp)
                    )
                }
                if (!feedback.isCorrect) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.correct_answer_was,
                            feedback.correctAnswer.capitalizeForWordLanguage(wordLanguage)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
