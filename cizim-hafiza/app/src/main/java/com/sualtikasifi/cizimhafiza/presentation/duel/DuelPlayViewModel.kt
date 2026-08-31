package com.sualtikasifi.cizimhafiza.presentation.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.Duel
import com.sualtikasifi.cizimhafiza.domain.repository.DuelRepository
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import com.sualtikasifi.cizimhafiza.util.AnswerMatcher
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Correct/wrong feedback for one guess, local to the duel play flow — see GamePhase's GuessFeedback for the (unrelated) solo/online equivalent. */
data class DuelGuessFeedback(val isCorrect: Boolean, val correctAnswer: String)

data class DuelPlayUiState(
    val isLoading: Boolean = true,
    val duel: Duel? = null,
    val currentIndex: Int = 0,
    val userAnswer: String = "",
    val feedback: DuelGuessFeedback? = null,
    val correctCount: Int = 0,
    val score: Int = 0,
    val isComplete: Boolean = false,
    val errorMessage: UiText? = null
) {
    val currentWord: String? get() = duel?.items?.getOrNull(currentIndex)?.word
}

/**
 * The opponent's half of a duel: look at each of the challenger's drawings
 * (already-recorded strokes — see Duel.items) and guess it, one at a time,
 * with no clock. Deliberately untimed and hint-free, unlike GameViewModel's
 * guessing phase — an async challenge that can be opened days later has no
 * "speed" to reward, and adding a countdown here would just punish whoever
 * happened to get interrupted mid-round.
 */
@HiltViewModel
class DuelPlayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val duelRepository: DuelRepository
) : ViewModel() {

    private val duelId: String = checkNotNull(savedStateHandle[Screen.ArgDuelId])

    private val _uiState = MutableStateFlow(DuelPlayUiState())
    val uiState: StateFlow<DuelPlayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            duelRepository.getDuel(duelId)
                .onSuccess { duel ->
                    _uiState.update {
                        it.copy(isLoading = false, duel = duel, errorMessage = if (duel == null) UiText.of(R.string.duel_not_found) else null)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = UiText.of(R.string.duel_not_found)) }
                }
        }
    }

    fun onAnswerChanged(text: String) {
        if (_uiState.value.feedback != null) return
        _uiState.update { it.copy(userAnswer = text) }
        val target = _uiState.value.currentWord ?: return
        // Auto-submits on an exact (tolerant) match, same UX as the normal
        // guess screen — typing the last letter is the "submit" action.
        if (text.isNotBlank() && AnswerMatcher.isCorrect(userAnswer = text, target = target)) {
            submitGuess(text)
        }
    }

    fun submitGuess(answer: String) {
        val state = _uiState.value
        if (state.feedback != null) return
        val target = state.currentWord ?: return
        val correct = AnswerMatcher.isCorrect(userAnswer = answer, target = target)
        _uiState.update {
            it.copy(
                feedback = DuelGuessFeedback(isCorrect = correct, correctAnswer = target),
                correctCount = it.correctCount + if (correct) 1 else 0,
                // Flat points, no speed bonus — see the class doc on why a
                // duel is deliberately untimed.
                score = it.score + if (correct) GameConstants.POINTS_CORRECT else 0
            )
        }
        viewModelScope.launch {
            delay(1_000)
            advance()
        }
    }

    fun skip() = submitGuess("")

    private fun advance() {
        val state = _uiState.value
        val duel = state.duel ?: return
        val nextIndex = state.currentIndex + 1
        if (nextIndex < duel.items.size) {
            _uiState.update { it.copy(currentIndex = nextIndex, userAnswer = "", feedback = null) }
        } else {
            _uiState.update { it.copy(isComplete = true) }
            // state was read at the top of this function, AFTER submitGuess's
            // own update already folded in this final guess — so it's the
            // true final total, not a stale pre-last-guess snapshot.
            viewModelScope.launch {
                duelRepository.submitDuelResult(
                    duelId = duelId,
                    opponentScore = state.score,
                    opponentCorrectCount = state.correctCount
                )
            }
        }
    }
}
