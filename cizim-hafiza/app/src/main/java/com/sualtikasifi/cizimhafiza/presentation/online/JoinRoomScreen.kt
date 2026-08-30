package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun JoinRoomScreen(
    onJoined: (roomCode: String) -> Unit,
    viewModel: JoinRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                text = stringResource(R.string.online_join_room),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = uiState.nickname,
                onValueChange = viewModel::setNickname,
                label = { Text(stringResource(R.string.online_nickname_label)) },
                singleLine = true,
                shape = PillShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.roomCode,
                onValueChange = viewModel::setRoomCode,
                label = { Text(stringResource(R.string.online_room_code_label)) },
                singleLine = true,
                shape = PillShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = stringResource(R.string.online_join_room_action),
                onClick = { viewModel.joinRoom(onJoined) },
                enabled = !uiState.isJoining,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
