package com.sualtikasifi.cizimhafiza.presentation.wordreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.model.WordReviewCounts
import com.sualtikasifi.cizimhafiza.domain.repository.WordReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordReviewUiState(
    val isLoading: Boolean = true,
    val word: Word? = null,
    val counts: WordReviewCounts? = null
) {
    val isFinished: Boolean get() = !isLoading && word == null
}

@HiltViewModel
class WordReviewViewModel @Inject constructor(
    private val repository: WordReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordReviewUiState())
    val uiState: StateFlow<WordReviewUiState> = _uiState.asStateFlow()

    init {
        loadNext()
    }

    /** One tap = keep + advance immediately, no confirmation dialog — this needs to be fast. */
    fun keep() = decide { repository.keep(it) }

    /** One tap = delete + advance immediately. */
    fun delete() = decide { repository.delete(it) }

    private fun decide(action: suspend (Int) -> Unit) {
        val wordId = _uiState.value.word?.id ?: return
        viewModelScope.launch {
            action(wordId)
            loadNext()
        }
    }

    private fun loadNext() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val next = repository.getNextPendingWord()
            val counts = repository.getCounts()
            _uiState.update { WordReviewUiState(isLoading = false, word = next, counts = counts) }
        }
    }
}
