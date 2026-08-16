package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard

@Composable
fun CreateRoomScreen(
    onRoomCreated: (roomCode: String) -> Unit,
    viewModel: CreateRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.online_create_room),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

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

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.select_word_count),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    uiState.availableCounts.forEach { count ->
                        SelectableCountCard(
                            count = count,
                            selected = count == uiState.selectedCount,
                            onClick = { viewModel.selectCount(count) },
                            modifier = Modifier.weight(1f),
                            verticalPadding = 10.dp,
                            textStyle = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.select_category),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                SelectableChip(
                    label = "🎨 ${stringResource(R.string.all_categories)}",
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    modifier = Modifier.fillMaxWidth(),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                uiState.categories.chunked(3).forEach { rowCategories ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowCategories.forEach { category ->
                            SelectableChip(
                                label = category,
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                modifier = Modifier.weight(1f),
                                horizontalPadding = 4.dp,
                                verticalPadding = 8.dp,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                fillWidth = true
                            )
                        }
                        repeat(3 - rowCategories.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.select_difficulty),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectableChip(
                    label = stringResource(R.string.all_difficulties),
                    selected = uiState.selectedDifficulty == null,
                    onClick = { viewModel.selectDifficulty(null) },
                    modifier = Modifier.fillMaxWidth(),
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEach { difficulty ->
                        SelectableChip(
                            label = difficultyLabel(difficulty),
                            selected = uiState.selectedDifficulty == difficulty,
                            onClick = { viewModel.selectDifficulty(difficulty) },
                            modifier = Modifier.weight(1f),
                            horizontalPadding = 6.dp,
                            verticalPadding = 8.dp,
                            style = MaterialTheme.typography.bodySmall,
                            fillWidth = true
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.isCreating) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.online_create_room_action),
                    onClick = { viewModel.createRoom(onRoomCreated) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
