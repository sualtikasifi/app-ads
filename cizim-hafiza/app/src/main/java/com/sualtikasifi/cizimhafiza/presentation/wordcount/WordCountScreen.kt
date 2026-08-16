package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard

@Composable
fun WordCountScreen(
    onStart: (count: Int, category: String?, difficulty: Difficulty?, mode: GameMode) -> Unit,
    viewModel: WordCountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        // Fixed, non-scrolling layout: every size/spacing below is tuned to
        // fit a single screen so nothing needs to scroll to be reached.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.select_word_count),
                    style = MaterialTheme.typography.headlineMedium,
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
                    text = stringResource(R.string.select_mode),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameMode.entries.forEach { mode ->
                        SelectableChip(
                            label = "${modeEmoji(mode)} ${modeLabel(mode)}",
                            selected = uiState.selectedMode == mode,
                            onClick = { viewModel.selectMode(mode) }
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
                // "Tümü" spans the full row on its own; the rest of the
                // categories are laid out as a fixed 3-per-row grid (no
                // horizontal or vertical scrolling) so everything is always
                // visible at once.
                SelectableChip(
                    label = "${categoryEmoji(null)} ${stringResource(R.string.all_categories)}",
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
                                label = "${categoryEmoji(category)} $category",
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
                        // Pad the last, possibly-incomplete row so its chips
                        // stay the same width as the full rows above them.
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
            }

            PrimaryButton(
                text = stringResource(R.string.start_game),
                onClick = {
                    onStart(uiState.selectedCount, uiState.selectedCategory, uiState.selectedDifficulty, uiState.selectedMode)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}

private fun categoryEmoji(category: String?): String = when (category) {
    "Hayvanlar" -> "🐶"
    "Eşyalar" -> "🧺"
    "Meslekler" -> "👮"
    "Spor" -> "⚽"
    "Doğa" -> "🌲"
    "Yiyecekler" -> "🍎"
    "Taşıtlar" -> "🚗"
    "Duygular" -> "😊"
    "Giyim" -> "👕"
    null -> "🎨"
    else -> "✨"
}

private fun modeEmoji(mode: GameMode): String = when (mode) {
    GameMode.NORMAL -> "⏱️"
    GameMode.RELAXED -> "🧘"
}

@Composable
private fun modeLabel(mode: GameMode): String = when (mode) {
    GameMode.NORMAL -> stringResource(R.string.mode_normal)
    GameMode.RELAXED -> stringResource(R.string.mode_relaxed)
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
