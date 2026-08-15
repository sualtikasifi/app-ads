package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(text = stringResource(R.string.select_word_count), style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.availableCounts.forEach { count ->
                        SelectableCountCard(
                            count = count,
                            selected = count == uiState.selectedCount,
                            onClick = { viewModel.selectCount(count) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(text = stringResource(R.string.select_mode), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameMode.entries.forEach { mode ->
                        SelectableChip(
                            label = "${modeEmoji(mode)} ${modeLabel(mode)}",
                            selected = uiState.selectedMode == mode,
                            onClick = { viewModel.selectMode(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(text = stringResource(R.string.select_category), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        SelectableChip(
                            label = "${categoryEmoji(null)} ${stringResource(R.string.all_categories)}",
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) }
                        )
                    }
                    items(uiState.categories) { category ->
                        SelectableChip(
                            label = "${categoryEmoji(category)} $category",
                            selected = uiState.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(text = stringResource(R.string.select_difficulty), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectableChip(
                        label = stringResource(R.string.all_difficulties),
                        selected = uiState.selectedDifficulty == null,
                        onClick = { viewModel.selectDifficulty(null) }
                    )
                    Difficulty.entries.forEach { difficulty ->
                        SelectableChip(
                            label = difficultyLabel(difficulty),
                            selected = uiState.selectedDifficulty == difficulty,
                            onClick = { viewModel.selectDifficulty(difficulty) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.words_available, uiState.wordsAvailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PrimaryButton(
                text = stringResource(R.string.start_game),
                onClick = {
                    onStart(uiState.selectedCount, uiState.selectedCategory, uiState.selectedDifficulty, uiState.selectedMode)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
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
    null -> "🎨"
    else -> "✨"
}

private fun modeEmoji(mode: GameMode): String = when (mode) {
    GameMode.NORMAL -> "⏱️"
    GameMode.RELAXED -> "🧘"
    GameMode.TIME_ATTACK -> "⚡"
}

@Composable
private fun modeLabel(mode: GameMode): String = when (mode) {
    GameMode.NORMAL -> stringResource(R.string.mode_normal)
    GameMode.RELAXED -> stringResource(R.string.mode_relaxed)
    GameMode.TIME_ATTACK -> stringResource(R.string.mode_time_attack)
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}
