package com.sualtikasifi.cizimhafiza.presentation.duel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenHeader
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun DuelPlayScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: DuelPlayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler { onBack() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(
                title = uiState.duel?.let { stringResource(R.string.duel_play_title, it.challengerName) }
                    ?: stringResource(R.string.duel_play_title_generic),
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(18.dp))

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage!!.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                uiState.isComplete -> DuelPlayCompleteContent(
                    correctCount = uiState.correctCount,
                    totalWords = uiState.duel?.totalWords ?: 0,
                    score = uiState.score,
                    onDone = onFinished
                )
                else -> DuelPlayContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DuelPlayContent(uiState: DuelPlayUiState, viewModel: DuelPlayViewModel) {
    val duel = uiState.duel ?: return
    val item = duel.items.getOrNull(uiState.currentIndex) ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.online_correct_of_total, uiState.currentIndex + 1, duel.totalWords),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        StrokeCanvas(
            strokes = item.strokes,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(CardWhite)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.feedback != null) {
            RaisedCard(
                corner = 18.dp,
                face = if (uiState.feedback.isCorrect) CorrectGreen.copy(alpha = 0.15f) else WrongRed.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (uiState.feedback.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (uiState.feedback.isCorrect) CorrectGreen else WrongRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.feedback.correctAnswer,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = uiState.userAnswer,
                onValueChange = viewModel::onAnswerChanged,
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
                modifier = Modifier.fillMaxWidth().imePadding()
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecondaryButton(
                text = stringResource(R.string.skip_guess),
                onClick = viewModel::skip,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DuelPlayCompleteContent(correctCount: Int, totalWords: Int, score: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconWell(icon = Icons.Filled.EmojiEvents, size = 64.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.duel_play_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.online_correct_of_total, correctCount, totalWords),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.total_score, score),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.duel_play_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}
