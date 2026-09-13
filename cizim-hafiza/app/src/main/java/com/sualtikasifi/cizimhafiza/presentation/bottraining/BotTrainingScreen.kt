package com.sualtikasifi.cizimhafiza.presentation.bottraining

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.DrawableCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.dotGridBackground
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun BotTrainingScreen(
    onBack: () -> Unit,
    // The two word-curation tools used to be registered in NavGraph with
    // nothing anywhere navigating to them — unreachable screens shipping in
    // every APK. They are developer tools like this screen, and the review
    // batches they triage (assets/word_review_batch_*.json, ~1400 candidate
    // words seeded with approved = false) are useless without them, so they
    // hang off this screen rather than being deleted: removing the single
    // "Bot Eğitim" tile later takes all three out together.
    onWordReview: () -> Unit = {},
    onDifficultyReview: () -> Unit = {},
    viewModel: BotTrainingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val wordLanguage = currentWordLanguage()

    // No title bar: the back button floats directly on the page's own
    // background instead of sitting in a separate, differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clears the floating back button (see ScreenTopActions).
            Spacer(modifier = Modifier.height(TopActionsClearance))
            Text(
                text = stringResource(R.string.bot_training_progress_format, uiState.trainedCount, uiState.totalCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.word == null && uiState.errorMessage != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.errorMessage!!.asString(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        SecondaryButton(
                            text = stringResource(R.string.retry_action),
                            onClick = viewModel::retry,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                uiState.isFinished -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.bot_training_finished_message),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = AppTheme.tokens.success
                    )
                }
                else -> {
                    val word = uiState.word!!
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            text = word.category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = word.text.capitalizeForWordLanguage(wordLanguage),
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    key(word.id) {
                        DrawableCanvas(
                            liveStrokes = uiState.strokes,
                            onStrokeFinished = viewModel::onStrokeFinished,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(top = 16.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surface)
                                .dotGridBackground(dotColor = MaterialTheme.colorScheme.outline, spacing = 20.dp, radius = 1.dp)
                                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.large)
                        )
                    }

                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message.asString(),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        SecondaryButton(
                            text = stringResource(R.string.clear_canvas),
                            onClick = viewModel::onClearCanvas,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = stringResource(R.string.bot_training_skip),
                            onClick = viewModel::skipWord,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.bot_training_save_next),
                        onClick = viewModel::saveAndNext,
                        enabled = uiState.strokes.isNotEmpty() && !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }
        }
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            RaisedIconButton(
                icon = Icons.Filled.FactCheck,
                contentDescription = stringResource(R.string.menu_word_review),
                onClick = onWordReview
            )
            Spacer(modifier = Modifier.width(10.dp))
            RaisedIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.menu_difficulty_review),
                onClick = onDifficultyReview
            )
        }
        }
    }
}
