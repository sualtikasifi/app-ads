package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.TopActionsClearance
import com.sualtikasifi.cizimhafiza.presentation.common.SectionLabel
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.wordCategoryColor

@Composable
fun WordCountScreen(
    onStart: (count: Int, category: String?, difficulty: Difficulty?, mode: GameMode) -> Unit,
    onBack: () -> Unit,
    viewModel: WordCountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Clears the floating back button (see ScreenTopActions).
            Spacer(modifier = Modifier.height(TopActionsClearance))

            // Scrolls rather than being squeezed to fit: the section list has
            // grown past what a small phone can show at once, and clipping the
            // difficulty row off the bottom is worse than a short scroll.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    uiState.availableCounts.forEach { count ->
                        SelectableCountCard(
                            count = count,
                            selected = count == uiState.selectedCount,
                            onClick = { viewModel.selectCount(count) },
                            modifier = Modifier.weight(1f),
                            verticalPadding = 12.dp,
                            textStyle = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.select_mode), Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameMode.entries.forEach { mode ->
                        SelectableChip(
                            label = "${modeEmoji(mode)}  ${modeLabel(mode)}",
                            selected = uiState.selectedMode == mode,
                            onClick = { viewModel.selectMode(mode) },
                            modifier = Modifier.weight(1f),
                            horizontalPadding = 10.dp,
                            verticalPadding = 12.dp,
                            style = MaterialTheme.typography.bodyMedium,
                            fillWidth = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionLabel(stringResource(R.string.select_category), Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                // "Tümü" spans the full row on its own; the rest are a fixed
                // 3-per-row grid. Each category carries its own accent color
                // when selected (see WordCategoryColors) so the grid reads as
                // a set of distinct places rather than nine identical pills.
                SelectableChip(
                    label = "${categoryEmoji(null)}  ${stringResource(R.string.all_categories)}",
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    modifier = Modifier.fillMaxWidth(),
                    horizontalPadding = 12.dp,
                    verticalPadding = 12.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true,
                    accent = wordCategoryColor(null)
                )
                Spacer(modifier = Modifier.height(8.dp))
                uiState.categories.chunked(3).forEach { rowCategories ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowCategories.forEach { category ->
                            SelectableChip(
                                label = "${categoryEmoji(category)}\n$category",
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                modifier = Modifier.weight(1f),
                                horizontalPadding = 4.dp,
                                verticalPadding = 10.dp,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                fillWidth = true,
                                accent = wordCategoryColor(category)
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

                Spacer(modifier = Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.select_difficulty), Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                SelectableChip(
                    label = stringResource(R.string.all_difficulties),
                    selected = uiState.selectedDifficulty == null,
                    onClick = { viewModel.selectDifficulty(null) },
                    modifier = Modifier.fillMaxWidth(),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    style = MaterialTheme.typography.bodyMedium,
                    fillWidth = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEach { difficulty ->
                        SelectableChip(
                            label = difficultyLabel(difficulty),
                            selected = uiState.selectedDifficulty == difficulty,
                            onClick = { viewModel.selectDifficulty(difficulty) },
                            modifier = Modifier.weight(1f),
                            horizontalPadding = 8.dp,
                            verticalPadding = 12.dp,
                            style = MaterialTheme.typography.bodyMedium,
                            fillWidth = true,
                            accent = difficultyAccent(difficulty)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            PrimaryButton(
                text = stringResource(R.string.start_game),
                onClick = {
                    onStart(uiState.selectedCount, uiState.selectedCategory, uiState.selectedDifficulty, uiState.selectedMode)
                },
                height = 60.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        ScreenTopActions(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
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

/**
 * Green→amber→rust, so difficulty reads at a glance before the label is
 * read. Composable so the three accents follow the active palette — the
 * light theme's greens and reds are far too dark to sit on a dark page.
 */
@Composable
private fun difficultyAccent(difficulty: Difficulty) = when (difficulty) {
    Difficulty.EASY -> AppTheme.tokens.success
    Difficulty.MEDIUM -> AppTheme.tokens.gold
    Difficulty.HARD -> MaterialTheme.colorScheme.error
}
