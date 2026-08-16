package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.RoomAlreadyStartedException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomFullException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomNotFoundException
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinRoomUiState(
    val nickname: String = "",
    val roomCode: String = "",
    val isJoining: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class JoinRoomViewModel @Inject constructor(
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinRoomUiState(nickname = settingsRepository.nickname.value))
    val uiState: StateFlow<JoinRoomUiState> = _uiState.asStateFlow()

    fun setNickname(name: String) = _uiState.update { it.copy(nickname = name, errorMessage = null) }

    fun setRoomCode(code: String) {
        val digitsOnly = code.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(roomCode = digitsOnly, errorMessage = null) }
    }

    fun joinRoom(onJoined: (roomCode: String) -> Unit) {
        val state = _uiState.value
        if (state.roomCode.length != 6) {
            _uiState.update { it.copy(errorMessage = "6 haneli kodu gir") }
            return
        }
        val nickname = state.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            onlineGameRepository.joinRoom(state.roomCode, nickname)
                .onSuccess {
                    _uiState.update { it.copy(isJoining = false) }
                    onJoined(state.roomCode)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isJoining = false, errorMessage = friendlyMessage(error)) }
                }
        }
    }

    private fun friendlyMessage(error: Throwable): String = when (error) {
        is RoomNotFoundException -> "Bu kodla bir oda bulunamadı"
        is RoomFullException -> "Bu oda dolu"
        is RoomAlreadyStartedException -> "Bu oyun zaten başladı"
        else -> "Bağlanılamadı, tekrar dene"
    }
}
