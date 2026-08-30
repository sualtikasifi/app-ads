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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

data class BotTrainingUiState(
    val isLoading: Boolean = true,
    val word: Word? = null,
    val strokes: List<DrawingStroke> = emptyList(),
    val trainedCount: Int = 0,
    val totalCount: Int = 0,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
) {
    /** Everything except locally-skipped-this-session words is trained — nothing left to draw. */
    val isFinished: Boolean get() = !isLoading && word == null
}

@HiltViewModel
class BotTrainingViewModel @Inject constructor(
    private val repository: BotTrainingRepository
) : ViewModel() {

    // Two people can train from two different phones at once (training
    // data is shared, not per-device) — always showing the literal first
    // untrained word would have both of them land on the same word at the
    // same time. Picking randomly among the earliest N untrained words
    // instead (still within the easy→hard ordering, just not pinned to its
    // very first entry) makes that collision unlikely without abandoning
    // the difficulty progression.
    private companion object {
        const val NEXT_WORD_POOL = 20

        /**
         * How long to wait for the first trained-word list before giving up.
         * Generous on purpose: the fallback path (see
         * BotTrainingRepositoryImpl.listenToCollection) can mean downloading
         * the whole trained-words collection — several MB — on top of
         * whatever the connection itself needs, not just one small doc.
         */
        const val SERVER_SYNC_TIMEOUT_MS = 45_000L
    }

    private val _uiState = MutableStateFlow(BotTrainingUiState())
    val uiState: StateFlow<BotTrainingUiState> = _uiState.asStateFlow()

    private var allWords: List<Word> = emptyList()
    private var lastTrainedIds: Set<Int> = emptySet()

    // Words skipped in this screen session only (never persisted) — lets the
    // trainer move past a word that's awkward to draw right now without
    // getting stuck showing the same "next untrained" word forever.
    private val skippedIds = mutableSetOf<Int>()

    private fun pickNextWord(trainedIds: Set<Int>): Word? =
        allWords.asSequence()
            .filter { it.id !in trainedIds && it.id !in skippedIds }
            .take(NEXT_WORD_POOL)
            .toList()
            .randomOrNull()

    init {
        viewModelScope.launch {
            allWords = repository.getAllWordsOrdered()
            repository.observeTrainedWordIds()
                .catch { _uiState.update { it.copy(isLoading = false, errorMessage = UiText.of(R.string.error_words_load_failed)) } }
                .collect { trainedIds -> showNextWord(trainedIds) }
        }
        // observeTrainedWordIds deliberately stays silent until it has heard
        // from the server (see its comment — a stale cached list hands out
        // already-trained words). With no connection that silence never
        // ends, so without this the screen would just spin forever with no
        // explanation.
        viewModelScope.launch {
            delay(SERVER_SYNC_TIMEOUT_MS)
            _uiState.update { current ->
                if (!current.isLoading) current
                else current.copy(
                    isLoading = false,
                    errorMessage = UiText.of(R.string.error_trained_words_unavailable)
                )
            }
        }
    }

    private fun showNextWord(trainedIds: Set<Int>) {
        lastTrainedIds = trainedIds
        val next = pickNextWord(trainedIds)
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
        val next = pickNextWord(lastTrainedIds)
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
                    _uiState.update { it.copy(isSaving = false, errorMessage = UiText.of(R.string.error_save_failed)) }
                }
        }
    }
}
