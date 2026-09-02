package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard
import com.sualtikasifi.cizimhafiza.presentation.common.AppTextField
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.asString

@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onRoomCreated: (roomCode: String) -> Unit,
    viewModel: CreateRoomViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Clears the floating back button (see ScreenTopActions).
            Spacer(modifier = Modifier.height(TopActionsClearance))
            // Scrollable, with the create button pinned below it: this screen
            // stacks a nickname field, five word-count cards, ten category
            // chips, four difficulty chips and two mode chips, which only
            // ever fitted a phone screen exactly. One step up in text size
            // (or a shorter device, or a larger system font) pushed the
            // difficulty row and the button off the bottom with no way to
            // reach them.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppTextField(
                    value = uiState.nickname,
                    onValueChange = viewModel::setNickname,
                    label = stringResource(R.string.online_nickname_label),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
                                horizontalPadding = 6.dp,
                                verticalPadding = 12.dp,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                fillWidth = true
                            )
                        }
                        repeat(3 - rowCategories.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
                            horizontalPadding = 10.dp,
                            verticalPadding = 12.dp,
                            style = MaterialTheme.typography.bodyMedium,
                            fillWidth = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.online_room_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectableChip(
                        label = stringResource(R.string.online_room_mode_free_for_all),
                        selected = !uiState.teamMode,
                        onClick = { viewModel.setTeamMode(false) },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 10.dp,
                        verticalPadding = 12.dp,
                        style = MaterialTheme.typography.bodyMedium,
                        fillWidth = true
                    )
                    SelectableChip(
                        label = stringResource(R.string.online_room_mode_team),
                        selected = uiState.teamMode,
                        onClick = { viewModel.setTeamMode(true) },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 10.dp,
                        verticalPadding = 12.dp,
                        style = MaterialTheme.typography.bodyMedium,
                        fillWidth = true
                    )
                }

                uiState.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message.asString(),
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
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
