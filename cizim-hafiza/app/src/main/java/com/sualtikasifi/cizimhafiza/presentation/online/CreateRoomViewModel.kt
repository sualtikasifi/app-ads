package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Difficulty
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

data class CreateRoomUiState(
    val availableCounts: List<Int> = GameConstants.WORD_COUNT_OPTIONS,
    val selectedCount: Int = GameConstants.WORD_COUNT_OPTIONS.first(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val selectedDifficulty: Difficulty? = null,
    val nickname: String = "",
    val isCreating: Boolean = false,
    val errorMessage: UiText? = null
)

@HiltViewModel
class CreateRoomViewModel @Inject constructor(
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateRoomUiState())
    val uiState: StateFlow<CreateRoomUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = getWordsForGameUseCase.getCategories()
            _uiState.update { it.copy(categories = categories, nickname = settingsRepository.nickname.value) }
        }
    }

    fun selectCount(count: Int) = _uiState.update { it.copy(selectedCount = count) }
    fun selectCategory(category: String?) = _uiState.update { it.copy(selectedCategory = category) }
    fun selectDifficulty(difficulty: Difficulty?) = _uiState.update { it.copy(selectedDifficulty = difficulty) }
    fun setNickname(name: String) = _uiState.update { it.copy(nickname = name, errorMessage = null) }

    fun createRoom(onCreated: (roomCode: String) -> Unit) {
        val state = _uiState.value
        val nickname = state.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isCreating = true, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            // Online matches are always the standard timed mode — RELAXED
            // (no countdown) would let one player stall the whole race.
            onlineGameRepository.createRoom(
                displayName = nickname,
                wordCount = state.selectedCount,
                category = state.selectedCategory,
                difficulty = state.selectedDifficulty,
                mode = GameMode.NORMAL
            ).onSuccess { code ->
                _uiState.update { it.copy(isCreating = false) }
                onCreated(code)
            }.onFailure { error ->
                _uiState.update { it.copy(isCreating = false, errorMessage = UiText.of(R.string.error_room_create_failed)) }
            }
        }
    }
}
