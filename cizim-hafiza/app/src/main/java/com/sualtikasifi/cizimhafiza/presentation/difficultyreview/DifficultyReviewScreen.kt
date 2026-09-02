package com.sualtikasifi.cizimhafiza.presentation.difficultyreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.Orange
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.DifficultyReviewShareUtil
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage
import kotlinx.coroutines.launch

@Composable
fun DifficultyReviewScreen(
    onBack: () -> Unit,
    viewModel: DifficultyReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val wordLanguage = currentWordLanguage()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // No title bar: the back button (and export action) float directly on
    // the page's own background instead of sitting in a separate,
    // differently-colored strip.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Extra top clearance for the floating back/export buttons below.
            Spacer(modifier = Modifier.height(44.dp))
            uiState.counts?.let { counts ->
                Text(
                    text = stringResource(R.string.difficulty_review_remaining_format, counts.pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.difficulty_review_progress_format,
                        counts.easy,
                        counts.medium,
                        counts.hard
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.isFinished -> DifficultyReviewFinished()
                    uiState.word != null -> {
                        val word = uiState.word!!
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = word.category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = word.text.capitalizeForWordLanguage(wordLanguage),
                                style = MaterialTheme.typography.displayMedium,
                                fontSize = 40.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (uiState.word != null && !uiState.isLoading) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = viewModel::setEasy,
                        colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen, contentColor = CardWhite),
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.SentimentSatisfied, contentDescription = null)
                            Text(text = stringResource(R.string.difficulty_review_easy), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Button(
                        onClick = viewModel::setMedium,
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = CardWhite),
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Bolt, contentDescription = null)
                            Text(text = stringResource(R.string.difficulty_review_medium), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Button(
                        onClick = viewModel::setHard,
                        colors = ButtonDefaults.buttonColors(containerColor = WrongRed, contentColor = CardWhite),
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Whatshot, contentDescription = null)
                            Text(text = stringResource(R.string.difficulty_review_hard), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RaisedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack
            )
            Spacer(modifier = Modifier.weight(1f))
            // Classification decisions only ever live on this device — this
            // is the only way they can reach the difficulty tags shipped to
            // every player (see DifficultyReviewShareUtil).
            RaisedIconButton(
                icon = Icons.Filled.Share,
                contentDescription = stringResource(R.string.difficulty_review_export),
                onClick = {
                    coroutineScope.launch {
                        val json = viewModel.exportReviewedDifficultiesJson()
                        DifficultyReviewShareUtil.shareReviewExport(context, json)
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun DifficultyReviewFinished() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CorrectGreen, modifier = Modifier.height(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.difficulty_review_finished_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.difficulty_review_finished_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
