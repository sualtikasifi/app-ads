package com.sualtikasifi.cizimhafiza.presentation.wordcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val selectedCategory: String? = null // null = all categories
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
        }
    }

    fun selectCount(count: Int) {
        _uiState.update { it.copy(selectedCount = count) }
    }

    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }
}
