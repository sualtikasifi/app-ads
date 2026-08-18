package com.sualtikasifi.cizimhafiza.presentation.bottraining

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.Word
import com.sualtikasifi.cizimhafiza.domain.repository.BotTrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BotTrainingUiState(
    val isLoading: Boolean = true,
    val word: Word? = null,
    val strokes: List<DrawingStroke> = emptyList(),
    val trainedCount: Int = 0,
    val totalCount: Int = 0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    /** Everything except locally-skipped-this-session words is trained — nothing left to draw. */
    val isFinished: Boolean get() = !isLoading && word == null
}

@HiltViewModel
class BotTrainingViewModel @Inject constructor(
    private val repository: BotTrainingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotTrainingUiState())
    val uiState: StateFlow<BotTrainingUiState> = _uiState.asStateFlow()

    private var allWords: List<Word> = emptyList()
    private var lastTrainedIds: Set<Int> = emptySet()

    // Words skipped in this screen session only (never persisted) — lets the
    // trainer move past a word that's awkward to draw right now without
    // getting stuck showing the same "next untrained" word forever.
    private val skippedIds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            allWords = repository.getAllWordsOrdered()
            repository.observeTrainedWordIds()
                .catch { }
                .collect { trainedIds -> showNextWord(trainedIds) }
        }
    }

    private fun showNextWord(trainedIds: Set<Int>) {
        lastTrainedIds = trainedIds
        val next = allWords.firstOrNull { it.id !in trainedIds && it.id !in skippedIds }
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                word = next,
                strokes = if (current.word?.id == next?.id) current.strokes else emptyList(),
                trainedCount = trainedIds.size,
                totalCount = allWords.size
            )
        }
    }

    fun onStrokeFinished(stroke: DrawingStroke) {
        _uiState.update { it.copy(strokes = it.strokes + listOf(stroke)) }
    }

    fun onClearCanvas() {
        _uiState.update { it.copy(strokes = emptyList()) }
    }

    fun skipWord() {
        val word = _uiState.value.word ?: return
        skippedIds.add(word.id)
        val next = allWords.firstOrNull { it.id !in lastTrainedIds && it.id !in skippedIds }
        _uiState.update { it.copy(word = next, strokes = emptyList()) }
    }

    fun saveAndNext() {
        val state = _uiState.value
        val word = state.word ?: return
        if (state.strokes.isEmpty() || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            repository.saveTraining(word, state.strokes)
                .onSuccess {
                    // observeTrainedWordIds() will emit the updated set and
                    // showNextWord() will advance — but that round-trip can
                    // take a beat, so clear isSaving/strokes right away for
                    // a snappy button response.
                    _uiState.update { it.copy(isSaving = false, strokes = emptyList()) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "Kaydedilemedi, tekrar dene") }
                }
        }
    }
}
