package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.repository.KickedFromRoomException
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
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

data class JoinRoomUiState(
    val nickname: String = "",
    val roomCode: String = "",
    val isJoining: Boolean = false,
    val errorMessage: UiText? = null
)

@HiltViewModel
class JoinRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository,
    private val botRoomEngine: BotRoomEngine
) : ViewModel() {

    // Pre-filled when opened via an invite link (karalak://join/482913);
    // empty for a plain in-app "Koda Katıl" tap.
    private val deepLinkedRoomCode: String =
        savedStateHandle.get<String>("roomCode")?.filter { it.isDigit() }?.take(6) ?: ""

    private val _uiState = MutableStateFlow(
        JoinRoomUiState(nickname = settingsRepository.nickname.value, roomCode = deepLinkedRoomCode)
    )
    val uiState: StateFlow<JoinRoomUiState> = _uiState.asStateFlow()

    fun setNickname(name: String) = _uiState.update { it.copy(nickname = name, errorMessage = null) }

    fun setRoomCode(code: String) {
        val digitsOnly = code.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(roomCode = digitsOnly, errorMessage = null) }
    }

    fun joinRoom(onJoined: (roomCode: String) -> Unit) {
        val state = _uiState.value
        if (state.roomCode.length != 6) {
            _uiState.update { it.copy(errorMessage = UiText.of(R.string.error_room_code_length)) }
            return
        }
        val nickname = state.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isJoining = true, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            // The bot room (see BotRoomEngine) has no real owner to have
            // created it ahead of time — bootstrap/repair it here before the
            // normal join call below, which would otherwise 404 on a room
            // that's never existed yet or is stuck from a past match.
            if (state.roomCode == BotRoomEngine.ROOM_CODE) {
                runCatching { botRoomEngine.ensureBootstrapped() }
            }
            // joinRoom() itself now allows joining a room that's already
            // PLAYING (as a pendingNextRound lobby-holder — see
            // OnlinePlayer.kt/WaitingRoomScreen.kt), so this only ever fails
            // with RoomAlreadyStartedException during the brief FINISHED
            // transition window between rounds.
            onlineGameRepository.joinRoom(state.roomCode, nickname)
                .onSuccess {
                    botRoomEngine.ensureRunning()
                    _uiState.update { it.copy(isJoining = false) }
                    onJoined(state.roomCode)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isJoining = false, errorMessage = friendlyMessage(error)) }
                }
        }
    }

    // Returns a UiText, not a String: the app ships a complete English
    // localization, and a literal here would show Turkish to an English
    // player the moment a join failed. See util/UiText.kt.
    private fun friendlyMessage(error: Throwable): UiText = when (error) {
        is RoomNotFoundException -> UiText.of(R.string.error_room_not_found)
        is RoomFullException -> UiText.of(R.string.error_room_full)
        is RoomAlreadyStartedException -> UiText.of(R.string.error_room_already_started)
        is KickedFromRoomException -> UiText.of(R.string.error_kicked_from_room, error.remainingMinutes)
        else -> UiText.of(R.string.error_no_connection)
    }
}
