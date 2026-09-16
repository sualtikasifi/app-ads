package com.sualtikasifi.cizimhafiza.presentation.botnames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.BotNameEntry
import com.sualtikasifi.cizimhafiza.domain.model.ReviewerIdentity
import com.sualtikasifi.cizimhafiza.domain.repository.BotNameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ModerationRepository
import com.sualtikasifi.cizimhafiza.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BotNamesUiState(
    val names: List<BotNameEntry> = emptyList(),
    val input: String = "",
    val isSubmitting: Boolean = false,
    /** Caught client-side before the write, not by firestore.rules — a typo re-typed is the expected case, not an attack. */
    val duplicateWarning: Boolean = false,
    val errorMessage: UiText? = null,
    /** Who this device is to the rules, so an empty list can be read rather than guessed at — see DrawingReportsScreen. */
    val identity: ReviewerIdentity? = null
)

@HiltViewModel
class BotNamesViewModel @Inject constructor(
    private val botNameRepository: BotNameRepository,
    private val moderationRepository: ModerationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotNamesUiState(identity = moderationRepository.identity()))
    val uiState: StateFlow<BotNamesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            botNameRepository.observeNames()
                .catch { _uiState.update { it.copy(errorMessage = UiText.of(R.string.bot_names_load_failed)) } }
                .collect { names -> _uiState.update { it.copy(names = names) } }
        }
    }

    fun setInput(value: String) {
        _uiState.update { it.copy(input = value, duplicateWarning = false) }
    }

    fun addName() {
        val current = _uiState.value
        val trimmed = current.input.trim()
        if (trimmed.isEmpty() || current.isSubmitting) return
        // Case-insensitive: "lekeAvcısı" and "lekeavcısı" read as the same
        // bot to a player, so they should count as the same duplicate here.
        if (current.names.any { it.name.equals(trimmed, ignoreCase = true) }) {
            _uiState.update { it.copy(duplicateWarning = true) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            botNameRepository.addName(trimmed)
                .onSuccess { _uiState.update { it.copy(input = "", isSubmitting = false) } }
                .onFailure { _uiState.update { it.copy(isSubmitting = false, errorMessage = UiText.of(R.string.bot_names_add_failed)) } }
        }
    }

    fun deleteName(id: String) {
        viewModelScope.launch { botNameRepository.deleteName(id) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
