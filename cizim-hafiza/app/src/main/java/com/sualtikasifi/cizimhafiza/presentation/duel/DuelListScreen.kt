package com.sualtikasifi.cizimhafiza.presentation.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Duel
import com.sualtikasifi.cizimhafiza.domain.model.DuelStatus
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenHeader
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed

@Composable
fun DuelListScreen(
    onBack: () -> Unit,
    onPlayDuel: (duelId: String) -> Unit,
    viewModel: DuelListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var resultDuel by remember { mutableStateOf<Duel?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                ScreenHeader(title = stringResource(R.string.duel_list_title), onBack = onBack)
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(text = stringResource(R.string.duel_list_incoming_title), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.incoming.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.duel_list_incoming_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(uiState.incoming, key = { it.id }) { duel ->
                    IncomingDuelCard(duel = duel, onClick = { onPlayDuel(duel.id) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = stringResource(R.string.duel_list_sent_title), style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.sent.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.duel_list_sent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(uiState.sent, key = { it.id }) { duel ->
                    SentDuelCard(
                        duel = duel,
                        onClick = {
                            if (duel.status == DuelStatus.COMPLETE) {
                                resultDuel = duel
                                if (!duel.seenByChallenger) viewModel.markSeen(duel.id)
                            }
                        },
                        onDelete = { viewModel.deleteDuel(duel.id) }
                    )
                }
            }
        }
    }

    resultDuel?.let { duel ->
        DuelResultDialog(duel = duel, onDismiss = { resultDuel = null })
    }
}

@Composable
private fun IncomingDuelCard(duel: Duel, onClick: () -> Unit) {
    RaisedCard(corner = 18.dp, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconWell(icon = Icons.Filled.EmojiEvents)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = duel.challengerName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.duel_list_word_count, duel.totalWords),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SentDuelCard(duel: Duel, onClick: () -> Unit, onDelete: () -> Unit) {
    RaisedCard(corner = 18.dp, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconWell(
                icon = if (duel.status == DuelStatus.AWAITING_OPPONENT) Icons.Filled.HourglassEmpty else Icons.Filled.EmojiEvents,
                tint = when {
                    duel.status == DuelStatus.AWAITING_OPPONENT -> MaterialTheme.colorScheme.onSurfaceVariant
                    duel.challengerWon == true -> CorrectGreen
                    duel.challengerWon == false -> WrongRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = duel.opponentName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when {
                        duel.status == DuelStatus.AWAITING_OPPONENT -> stringResource(R.string.duel_list_status_waiting)
                        duel.challengerWon == true -> stringResource(R.string.duel_list_status_won)
                        duel.challengerWon == false -> stringResource(R.string.duel_list_status_lost)
                        else -> stringResource(R.string.duel_list_status_tied)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!duel.seenByChallenger && duel.status == DuelStatus.COMPLETE) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.duel_list_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DuelResultDialog(duel: Duel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (duel.challengerWon) {
                    true -> stringResource(R.string.duel_list_status_won)
                    false -> stringResource(R.string.duel_list_status_lost)
                    null -> stringResource(R.string.duel_list_status_tied)
                }
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.duel_result_score_format, duel.challengerName, duel.challengerScore))
                Text(stringResource(R.string.duel_result_score_format, duel.opponentName, duel.opponentScore ?: 0))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
