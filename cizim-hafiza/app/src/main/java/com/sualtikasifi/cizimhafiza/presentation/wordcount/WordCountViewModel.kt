package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val selectedMode: GameMode = GameMode.NORMAL,
    val wordsAvailable: Int = 0
)

@HiltViewModel
class WordCountViewModel @Inject constructor(
    private val getWordsForGameUseCase: GetWordsForGameUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordCountUiState())
    val uiState: StateFlow<WordCountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = getWordsForGameUseCase.getCategories()
            _uiState.update { it.copy(categories = categories) }
            refreshAvailableCount()
        }
    }

    fun selectCount(count: Int) {
        _uiState.update { it.copy(selectedCount = count) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        refreshAvailableCount()
    }

    fun selectDifficulty(difficulty: Difficulty?) {
        _uiState.update { it.copy(selectedDifficulty = difficulty) }
        refreshAvailableCount()
    }

    fun selectMode(mode: GameMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    private fun refreshAvailableCount() {
        viewModelScope.launch {
            val state = _uiState.value
            val count = getWordsForGameUseCase.countWords(state.selectedCategory, state.selectedDifficulty)
            _uiState.update { it.copy(wordsAvailable = count) }
        }
    }
}
