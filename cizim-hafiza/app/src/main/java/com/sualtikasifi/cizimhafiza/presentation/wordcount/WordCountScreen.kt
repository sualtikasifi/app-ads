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
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableChip
import com.sualtikasifi.cizimhafiza.presentation.common.SelectableCountCard

@Composable
fun WordCountScreen(
    onStart: (count: Int, category: String?) -> Unit,
    viewModel: WordCountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
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

            Spacer(modifier = Modifier.height(36.dp))
            Text(text = stringResource(R.string.select_category), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.words_available, uiState.wordsInSelectedCategory),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = stringResource(R.string.start_game),
                onClick = { onStart(uiState.selectedCount, uiState.selectedCategory) },
                modifier = Modifier.fillMaxWidth()
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
