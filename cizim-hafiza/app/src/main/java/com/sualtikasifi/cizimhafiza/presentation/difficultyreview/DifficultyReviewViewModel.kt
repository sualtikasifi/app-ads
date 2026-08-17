package com.sualtikasifi.cizimhafiza.presentation.difficultyreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.DifficultyReviewCounts
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.DifficultyReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DifficultyReviewUiState(
    val isLoading: Boolean = true,
    val word: Word? = null,
    val counts: DifficultyReviewCounts? = null
) {
    val isFinished: Boolean get() = !isLoading && word == null
}

@HiltViewModel
class DifficultyReviewViewModel @Inject constructor(
    private val repository: DifficultyReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DifficultyReviewUiState())
    val uiState: StateFlow<DifficultyReviewUiState> = _uiState.asStateFlow()

    init {
        loadNext()
    }

    /** One tap = classify + advance immediately, no confirmation dialog — this needs to be fast. */
    fun setEasy() = decide(Difficulty.EASY)
    fun setMedium() = decide(Difficulty.MEDIUM)
    fun setHard() = decide(Difficulty.HARD)

    private fun decide(difficulty: Difficulty) {
        val wordId = _uiState.value.word?.id ?: return
        viewModelScope.launch {
            repository.setDifficulty(wordId, difficulty)
            loadNext()
        }
    }

    private fun loadNext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val next = repository.getNextPendingWord()
            val counts = repository.getCounts()
            _uiState.update { DifficultyReviewUiState(isLoading = false, word = next, counts = counts) }
        }
    }

    /** Called from the Composable (which owns the Context needed to launch the share sheet). */
    suspend fun exportReviewedDifficultiesJson(): String = repository.exportReviewedDifficultiesJson()
}
