package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.local.WordPoolSynchronizer
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.util.GameConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordCountUiState(
    val availableCounts: List<Int> = GameConstants.WORD_COUNT_OPTIONS,
    val selectedCount: Int = GameConstants.WORD_COUNT_OPTIONS.first(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null, // null = all categories
    val selectedDifficulty: Difficulty? = null, // null = all difficulties
    val selectedMode: GameMode = GameMode.NORMAL
)

@HiltViewModel
class WordCountViewModel @Inject constructor(
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val wordPoolSynchronizer: WordPoolSynchronizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordCountUiState())
    val uiState: StateFlow<WordCountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Without this, a cold start (or a language toggle whose reseed
            // is still in flight — see WordPoolSynchronizer's own kdoc)
            // could load this screen's category chips from whichever
            // language's rows Room happened to still hold, and they never
            // got a second chance to refresh: getCategories() below only
            // ever ran once, right here.
            wordPoolSynchronizer.ensureSynced()
            val categories = getWordsForGameUseCase.getCategories()
            _uiState.update { it.copy(categories = categories) }
        }
    }

    fun selectCount(count: Int) {
        _uiState.update { it.copy(selectedCount = count) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectDifficulty(difficulty: Difficulty?) {
        _uiState.update { it.copy(selectedDifficulty = difficulty) }
    }

    fun selectMode(mode: GameMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }
}
