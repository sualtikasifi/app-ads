package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitingRoomUiState(
    val room: OnlineRoom? = null,
    val isStarting: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class WaitingRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val onlineGameRepository: OnlineGameRepository
) : ViewModel() {

    val roomCode: String = checkNotNull(savedStateHandle["roomCode"])
    val myUid: String? get() = onlineGameRepository.currentUid

    private val _uiState = MutableStateFlow(WaitingRoomUiState())
    val uiState: StateFlow<WaitingRoomUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            onlineGameRepository.observeRoom(roomCode).collect { room ->
                _uiState.update { it.copy(room = room) }
            }
        }
    }

    fun toggleReady() {
        val amReady = _uiState.value.room?.players?.find { it.uid == myUid }?.ready ?: false
        viewModelScope.launch { onlineGameRepository.setReady(roomCode, !amReady) }
    }

    /** Host-only: locks in the shared word list (same words for both players) and starts the match. */
    fun startGame() {
        val room = _uiState.value.room ?: return
        if (room.hostUid != myUid || _uiState.value.isStarting) return
        _uiState.update { it.copy(isStarting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val words = getWordsForGameUseCase(room.wordCount, room.category, room.difficulty)
                onlineGameRepository.startGame(roomCode, words.map { it.id })
            }.onFailure {
                _uiState.update { state -> state.copy(isStarting = false, errorMessage = "Oyun başlatılamadı, tekrar dene") }
            }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch { onlineGameRepository.leaveRoom(roomCode) }
    }
}
