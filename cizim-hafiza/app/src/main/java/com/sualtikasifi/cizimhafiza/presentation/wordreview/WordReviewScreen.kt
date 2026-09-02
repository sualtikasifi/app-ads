package com.sualtikasifi.cizimhafiza.presentation.wordreview

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.WordReviewShareUtil
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage
import kotlinx.coroutines.launch

@Composable
fun WordReviewScreen(
    onBack: () -> Unit,
    viewModel: WordReviewViewModel = hiltViewModel()
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
                    text = stringResource(R.string.word_review_remaining_format, counts.pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.word_review_progress_format, counts.kept, counts.deleted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.isFinished -> WordReviewFinished()
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
                        onClick = viewModel::keep,
                        colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen, contentColor = CardWhite),
                        modifier = Modifier.fillMaxWidth().height(84.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null)
                            Text(text = stringResource(R.string.word_review_keep), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Button(
                        onClick = viewModel::delete,
                        colors = ButtonDefaults.buttonColors(containerColor = WrongRed, contentColor = CardWhite),
                        modifier = Modifier.fillMaxWidth().height(84.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(text = stringResource(R.string.word_review_delete), style = MaterialTheme.typography.titleLarge)
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
            // Review decisions only ever live on this device — this is the
            // only way they can reach the word pool everyone else plays
            // (see WordReviewShareUtil).
            RaisedIconButton(
                icon = Icons.Filled.Share,
                contentDescription = stringResource(R.string.word_review_export),
                onClick = {
                    coroutineScope.launch {
                        val json = viewModel.exportReviewedWordsJson()
                        WordReviewShareUtil.shareReviewExport(context, json)
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun WordReviewFinished() {
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
                text = stringResource(R.string.word_review_finished_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.word_review_finished_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
