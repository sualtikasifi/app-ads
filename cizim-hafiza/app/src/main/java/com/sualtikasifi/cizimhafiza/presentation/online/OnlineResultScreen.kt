package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.capitalizeTr

@Composable
fun OnlineResultScreen(
    onRematchStarted: (roomCode: String) -> Unit,
    onMainMenu: () -> Unit,
    viewModel: OnlineResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val room = uiState.room
    val myUid = viewModel.myUid
    val me = room?.players?.find { it.uid == myUid }
    val opponent = room?.players?.find { it.uid != myUid }

    LaunchedEffect(uiState.navigateToRematchRoomCode) {
        uiState.navigateToRematchRoomCode?.let(onRematchStarted)
    }

    if (room == null || me == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    // Opponent hasn't submitted their result yet — their totalScore field is
    // still the pre-game 0, so showing the comparison now would misleadingly
    // look like they already lost. Wait (with reactions still available)
    // until they've genuinely finished.
    if (opponent == null || !opponent.finished) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.online_you_finished, me.totalScore),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.online_waiting_for_opponent_result),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (opponent != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Tall enough for the full bubble (emoji + caption line),
                    // not just the emoji — a too-short box let the bubble's
                    // bottom half render underneath the ReactionSendRow below it.
                    Box(modifier = Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                        ReactionOverlay(reactions = uiState.reactions, myUid = myUid)
                    }
                    ReactionSendRow(onSend = viewModel::sendReaction)
                }
            }
        }
        return
    }

    var showingOpponent by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<ResultItem?>(null) }

    val amWinner = me.totalScore > opponent.totalScore
    val isTie = me.totalScore == opponent.totalScore

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.game_over),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = when {
                    isTie -> stringResource(R.string.online_result_tie)
                    amWinner -> stringResource(R.string.online_result_win)
                    else -> stringResource(R.string.online_result_lose)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    isTie -> MaterialTheme.colorScheme.onSurfaceVariant
                    amWinner -> CorrectGreen
                    else -> WrongRed
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)
            ) {
                PlayerScoreCard(name = me.displayName, score = me.totalScore, isYou = true, modifier = Modifier.weight(1f))
                PlayerScoreCard(name = opponent.displayName, score = opponent.totalScore, isYou = false, modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = stringResource(R.string.online_my_drawings),
                    onClick = { showingOpponent = false },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.online_opponent_drawings),
                    onClick = { showingOpponent = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val items = if (showingOpponent) uiState.opponentItems else uiState.myItems
            if (uiState.isLoadingItems) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                StrokeCanvas(
                                    strokes = item.strokes,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(CardWhite)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                        .clickable { previewItem = item }
                                )
                                val badgeColor = if (item.isCorrect) CorrectGreen else WrongRed
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = CardWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.word.capitalizeTr(),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                ReactionOverlay(reactions = uiState.reactions, myUid = myUid)
            }
            ReactionSendRow(onSend = viewModel::sendReaction, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                SecondaryButton(text = stringResource(R.string.main_menu), onClick = onMainMenu, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(if (uiState.rematchRequested) R.string.online_rematch_waiting else R.string.play_again),
                    onClick = viewModel::requestRematch,
                    enabled = !uiState.rematchRequested,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val itemToPreview = previewItem
    if (itemToPreview != null) {
        Dialog(
            onDismissRequest = { previewItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StrokeCanvas(
                        strokes = itemToPreview.strokes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(CardWhite)
                            .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                    )
                    Text(
                        text = itemToPreview.word.capitalizeTr(),
                        style = MaterialTheme.typography.titleLarge,
                        color = CardWhite,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                IconButton(
                    onClick = { previewItem = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = CardWhite)
                }
            }
        }
    }
}

@Composable
private fun PlayerScoreCard(name: String, score: Int, isYou: Boolean, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isYou) stringResource(R.string.online_you_label, name) else name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

