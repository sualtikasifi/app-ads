package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.KickedUser
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.InviteShareUtil

@Composable
fun WaitingRoomScreen(
    onGameStarted: (roomCode: String) -> Unit,
    onLeave: () -> Unit,
    viewModel: WaitingRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val room = uiState.room
    val myUid = viewModel.myUid
    val isHost = room != null && room.hostUid == myUid
    val me = room?.players?.find { it.uid == myUid }
    val others = room?.players?.filter { it.uid != myUid } ?: emptyList()
    val amPending = me?.pendingNextRound == true
    // Ready-check only applies to players actually in this round — a
    // pendingNextRound joiner (sitting out the round already in progress)
    // shouldn't block the others from starting/being "all ready".
    val activePlayers = room?.players?.filterNot { it.pendingNextRound } ?: emptyList()
    val allReady = activePlayers.size >= 2 && activePlayers.all { it.ready }
    val amReady = me?.ready == true

    var kickTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(room?.status, amPending) {
        if (room?.status == RoomStatus.PLAYING && !amPending) {
            onGameStarted(viewModel.roomCode)
        }
    }

    BackHandler {
        viewModel.leaveRoom()
        onLeave()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.online_waiting_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.online_room_code_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = viewModel.roomCode,
                    style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 8.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.online_invite_friend),
                onClick = { InviteShareUtil.shareRoomInvite(context, viewModel.roomCode) },
                icon = Icons.Filled.Share
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.online_room_occupancy,
                    room?.players?.size ?: 1,
                    GameConstants.MAX_ROOM_SIZE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (amPending) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.online_pending_next_round_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlayerRow(name = me?.displayName ?: "…", ready = amReady, isYou = true, pending = amPending)
                Spacer(modifier = Modifier.height(10.dp))
                if (others.isNotEmpty()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(others, key = { it.uid }) { player ->
                            PlayerRow(
                                name = player.displayName,
                                ready = player.ready,
                                isYou = false,
                                pending = player.pendingNextRound,
                                onKick = if (isHost) {
                                    { kickTarget = player.uid to player.displayName }
                                } else null
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.online_waiting_for_friend),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                }
            }

            if (isHost && room?.kickedUsers?.isNotEmpty() == true) {
                KickedUsersSection(kickedUsers = room.kickedUsers, onUnban = viewModel::unbanPlayer)
            }

            if (others.isNotEmpty()) {
                // Tall enough for the full bubble (sender name + emoji +
                // caption line), not just the emoji — a too-short box let
                // the bubble's bottom half render underneath the
                // ReactionSendRow below it.
                Box(modifier = Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    ReactionOverlay(reactions = uiState.reactions, myUid = myUid, players = room?.players ?: emptyList())
                }
                ReactionSendRow(onSend = viewModel::sendReaction, modifier = Modifier.padding(bottom = 12.dp))
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            when {
                amPending -> Unit // message already shown above; no ready/start controls for a sitting-out joiner
                others.isEmpty() -> Unit
                !allReady -> SecondaryButton(
                    text = stringResource(if (amReady) R.string.online_ready_cancel else R.string.online_ready),
                    onClick = viewModel::toggleReady,
                    modifier = Modifier.fillMaxWidth()
                )
                isHost -> if (uiState.isStarting) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.online_start_game),
                        onClick = viewModel::startGame,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> Text(
                    text = stringResource(R.string.online_waiting_for_host),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    kickTarget?.let { (targetUid, targetName) ->
        AlertDialog(
            onDismissRequest = { kickTarget = null },
            title = { Text(stringResource(R.string.online_kick_confirm_title)) },
            text = { Text(stringResource(R.string.online_kick_confirm_message, targetName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickPlayer(targetUid, targetName)
                    kickTarget = null
                }) {
                    Text(stringResource(R.string.online_kick_confirm_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { kickTarget = null }) {
                    Text(stringResource(R.string.online_kick_confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun KickedUsersSection(kickedUsers: List<KickedUser>, onUnban: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.online_kicked_users_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 140.dp)) {
            kickedUsers.forEach { kicked ->
                val remainingMinutes = ((kicked.untilMillis - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(0)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.online_kicked_user_remaining_format, kicked.displayName, remainingMinutes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onUnban(kicked.uid) }) {
                            Text(stringResource(R.string.online_unban_player))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    name: String,
    ready: Boolean,
    isYou: Boolean,
    pending: Boolean = false,
    onKick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = TextDark),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isYou) stringResource(R.string.online_you_label, name) else name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (pending) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.online_pending_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ready) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = CorrectGreen)
                }
                if (onKick != null) {
                    IconButton(onClick = onKick) {
                        Icon(
                            Icons.Filled.PersonRemove,
                            contentDescription = stringResource(R.string.online_kick_player),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
