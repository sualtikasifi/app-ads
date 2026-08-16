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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import com.sualtikasifi.cizimhafiza.presentation.theme.CorrectGreen
import com.sualtikasifi.cizimhafiza.presentation.theme.TextDark

@Composable
fun WaitingRoomScreen(
    onGameStarted: (roomCode: String) -> Unit,
    onLeave: () -> Unit,
    viewModel: WaitingRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val room = uiState.room
    val myUid = viewModel.myUid
    val isHost = room != null && room.hostUid == myUid
    val me = room?.players?.find { it.uid == myUid }
    val opponent = room?.players?.find { it.uid != myUid }
    val bothReady = room != null && room.players.size == 2 && room.players.all { it.ready }
    val amReady = me?.ready == true

    LaunchedEffect(room?.status) {
        if (room?.status == RoomStatus.PLAYING) {
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

            Spacer(modifier = Modifier.height(28.dp))

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlayerRow(name = me?.displayName ?: "…", ready = amReady, isYou = true)
                Spacer(modifier = Modifier.height(10.dp))
                if (opponent != null) {
                    PlayerRow(name = opponent.displayName, ready = opponent.ready, isYou = false)
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

            if (opponent != null) {
                Box(modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    ReactionOverlay(reactions = uiState.reactions, myUid = myUid)
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
                opponent == null -> Unit
                !bothReady -> SecondaryButton(
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
}

@Composable
private fun PlayerRow(name: String, ready: Boolean, isYou: Boolean) {
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
            }
            if (ready) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = CorrectGreen)
            }
        }
    }
}
