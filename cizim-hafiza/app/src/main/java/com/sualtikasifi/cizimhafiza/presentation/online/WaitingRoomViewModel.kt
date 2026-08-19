package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaitingRoomUiState(
    val room: OnlineRoom? = null,
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
    val reactions: List<Reaction> = emptyList()
)

@HiltViewModel
class WaitingRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val onlineGameRepository: OnlineGameRepository,
    botRoomEngine: BotRoomEngine
) : ViewModel() {

    val roomCode: String = checkNotNull(savedStateHandle["roomCode"])
    val myUid: String? get() = onlineGameRepository.currentUid

    private val _uiState = MutableStateFlow(WaitingRoomUiState())
    val uiState: StateFlow<WaitingRoomUiState> = _uiState.asStateFlow()

    init {
        // Harmless/no-op for any other room — only ever drives room 130246
        // (see BotRoomEngine) and only starts its listener once per process.
        botRoomEngine.ensureRunning()
        // Both Flows close with an exception on a Firestore listener error
        // (see OnlineGameRepositoryImpl.observeRoom/observeReactions) —
        // .catch{} keeps that from crashing the app; the screen just stops
        // updating until the listener recovers, same as a brief network drop.
        viewModelScope.launch {
            onlineGameRepository.observeRoom(roomCode)
                .catch { }
                .collect { room -> _uiState.update { it.copy(room = room) } }
        }
        viewModelScope.launch {
            onlineGameRepository.observeReactions(roomCode)
                .catch { }
                .collect { reactions -> _uiState.update { it.copy(reactions = reactions) } }
        }
    }

    // Fire-and-forget Firestore writes (reaction, ready toggle, leave below):
    // wrapped in runCatching, not left to throw, because a transient network
    // failure here would otherwise crash the whole app mid-match — these are
    // all safe to just silently fail and let the player retry the tap; there
    // is no local state to roll back since none was optimistically applied.
    fun sendReaction(emoji: String, messageKey: String) {
        viewModelScope.launch { runCatching { onlineGameRepository.sendReaction(roomCode, emoji, messageKey) } }
    }

    fun toggleReady() {
        val amReady = _uiState.value.room?.players?.find { it.uid == myUid }?.ready ?: false
        viewModelScope.launch { runCatching { onlineGameRepository.setReady(roomCode, !amReady) } }
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
        viewModelScope.launch { runCatching { onlineGameRepository.leaveRoom(roomCode) } }
    }

    /** Host-only (WaitingRoomScreen only shows the button when isHost) — removes and 30-minute-bans a player. */
    fun kickPlayer(targetUid: String, targetDisplayName: String) {
        viewModelScope.launch { runCatching { onlineGameRepository.kickPlayer(roomCode, targetUid, targetDisplayName) } }
    }

    /** Host-only — lifts an active kick ban early. */
    fun unbanPlayer(targetUid: String) {
        viewModelScope.launch { runCatching { onlineGameRepository.unbanPlayer(roomCode, targetUid) } }
    }
}
