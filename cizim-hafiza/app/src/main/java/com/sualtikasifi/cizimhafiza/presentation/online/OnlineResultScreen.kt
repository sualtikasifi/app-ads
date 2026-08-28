package com.sualtikasifi.cizimhafiza.presentation.online

import android.app.Activity
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.presentation.common.BotMascot
import com.sualtikasifi.cizimhafiza.presentation.common.BotMascotPose
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.StrokeCanvas
import com.sualtikasifi.cizimhafiza.presentation.common.currentWordLanguage
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.WrongRed
import com.sualtikasifi.cizimhafiza.util.capitalizeForWordLanguage
import com.sualtikasifi.cizimhafiza.util.placementEmoji

@Composable
fun OnlineResultScreen(
    onRematchStarted: (roomCode: String) -> Unit,
    onReturnToWaitingRoom: (roomCode: String) -> Unit,
    onMainMenu: () -> Unit,
    viewModel: OnlineResultViewModel = hiltViewModel()
) {
    val wordLanguage = currentWordLanguage()
    val uiState by viewModel.uiState.collectAsState()
    val room = uiState.room
    val myUid = viewModel.myUid
    val me = room?.players?.find { it.uid == myUid }
    // pendingNextRound players (joined mid-round — see OnlinePlayer.kt)
    // never played this round: excluded from every comparison below, same
    // as OnlineResultViewModel excludes them from the "is everyone done"
    // check.
    // left=true players have to go too, not just pendingNextRound ones —
    // OnlineResultViewModel already excludes both when it decides the round
    // is over, and without the same rule here a single player quitting
    // mid-match leaves everyone else stuck on "waiting for the others"
    // forever, since a departed player's finished flag never flips.
    val others = room?.players?.filter { it.uid != myUid && !it.pendingNextRound && !it.left } ?: emptyList()
    val context = LocalContext.current

    // Shown once the finished-round comparison is actually on-screen (the
    // same condition the content below waits on) — see AdManager's
    // placement doc. Keyed on the boolean so it only fires the instant this
    // flips from waiting to showing, not on every uiState update.
    val resultIsShowing = others.isNotEmpty() && others.all { it.finished }
    LaunchedEffect(resultIsShowing) {
        if (resultIsShowing) {
            (context as? Activity)?.let { viewModel.showInterstitial(it) }
        }
    }

    LaunchedEffect(uiState.navigateToRematchRoomCode) {
        uiState.navigateToRematchRoomCode?.let(onRematchStarted)
    }
    LaunchedEffect(uiState.navigateToWaitingRoomCode) {
        uiState.navigateToWaitingRoomCode?.let(onReturnToWaitingRoom)
    }

    if (room == null || me == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    // Not everyone has submitted their result yet — an unfinished player's
    // totalScore field is still the pre-game 0, so showing the comparison
    // now would misleadingly look like they already lost. Wait (with
    // reactions still available) until everyone's genuinely finished.
    if (others.isEmpty() || others.any { !it.finished }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .screenBackground()
                    .padding(padding).padding(24.dp),
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
                if (others.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    // Tall enough for the full bubble (sender name + emoji +
                    // caption line), not just the emoji — a too-short box let
                    // the bubble's bottom half render underneath the
                    // ReactionSendRow below it.
                    Box(modifier = Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                        ReactionOverlay(reactions = uiState.reactions, myUid = myUid, players = room.players)
                    }
                    ReactionSendRow(onSend = viewModel::sendReaction)
                }
            }
        }
        return
    }

    var previewItem by remember { mutableStateOf<ResultItem?>(null) }

    // "others" already excludes pendingNextRound joiners — this round's
    // comparison is only ever between the players who actually played it.
    val roundPlayers = listOfNotNull(me) + others
    val ranked = roundPlayers.sortedByDescending { it.totalScore }
    val myPlacement = ranked.indexOfFirst { it.uid == myUid } + 1

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.game_over),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.online_result_placement, myPlacement),
                style = MaterialTheme.typography.headlineSmall,
                color = if (myPlacement == 1) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )

            // Leaderboard: a scrollable list (not a fixed 2-up row) since a
            // room can have up to GameConstants.MAX_ROOM_SIZE players.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).heightIn(max = 240.dp)
            ) {
                val myScore = ranked.find { it.uid == myUid }?.totalScore ?: 0
                columnItems(ranked, key = { it.uid }) { player ->
                    val rank = ranked.indexOf(player) + 1
                    val mascotPose = when {
                        player.uid != BotRoomEngine.BOT_UID -> null
                        player.totalScore < myScore -> BotMascotPose.SAD
                        else -> BotMascotPose.HAPPY
                    }
                    PlayerScoreCard(
                        rank = rank,
                        name = player.displayName,
                        level = player.level,
                        score = player.totalScore,
                        correctCount = player.correctCount,
                        totalWords = player.correctCount + player.wrongCount,
                        isYou = player.uid == myUid,
                        mascotPose = mascotPose
                    )
                }
            }

            // Whose drawing gallery to show — a scrollable row of player-name
            // chips (not a binary toggle) since there can be up to
            // GameConstants.MAX_ROOM_SIZE players. These have to be
            // *selectable* chips rather than plain buttons: with a room this
            // size, an unhighlighted row gives no clue whose drawings are
            // currently on screen.
            Text(
                text = stringResource(R.string.online_whose_drawings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                roundPlayers.forEach { player ->
                    val label = if (player.uid == myUid) {
                        stringResource(R.string.online_you_label, player.displayName)
                    } else {
                        player.displayName
                    }
                    SelectableChip(
                        label = label,
                        selected = player.uid == uiState.selectedUid,
                        onClick = { viewModel.selectPlayer(player.uid) },
                        horizontalPadding = 14.dp,
                        verticalPadding = 10.dp,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val items = uiState.itemsByUid[uiState.selectedUid] ?: emptyList()
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
                                text = item.word.capitalizeForWordLanguage(wordLanguage),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                ReactionOverlay(reactions = uiState.reactions, myUid = myUid, players = room.players)
            }
            ReactionSendRow(onSend = viewModel::sendReaction, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

            if (uiState.rematchBlockedByNewJoiner) {
                Text(
                    text = stringResource(R.string.online_rematch_blocked_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                SecondaryButton(text = stringResource(R.string.main_menu), onClick = onMainMenu, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(if (uiState.rematchRequested) R.string.online_rematch_waiting else R.string.play_again),
                    onClick = viewModel::requestRematch,
                    enabled = !uiState.rematchRequested && !uiState.rematchBlockedByNewJoiner,
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
                        text = itemToPreview.word.capitalizeForWordLanguage(wordLanguage),
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
private fun PlayerScoreCard(
    rank: Int,
    name: String,
    level: Int,
    score: Int,
    correctCount: Int,
    totalWords: Int,
    isYou: Boolean,
    mascotPose: BotMascotPose? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = placementEmoji(rank) ?: "$rank.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 10.dp)
                )
                if (mascotPose != null) {
                    BotMascot(pose = mascotPose, modifier = Modifier.padding(end = 8.dp))
                } else {
                    LevelAvatar(
                        level = level,
                        frame = AvatarFrame.highestUnlockedFor(level),
                        size = 34.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isYou) stringResource(R.string.online_you_label, name) else name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    // The score alone doesn't say how the round actually went
                    // — "8/10 doğru" is what makes this a result table rather
                    // than just a ranking.
                    if (totalWords > 0) {
                        Text(
                            text = stringResource(R.string.online_correct_of_total, correctCount, totalWords),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "$score",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

