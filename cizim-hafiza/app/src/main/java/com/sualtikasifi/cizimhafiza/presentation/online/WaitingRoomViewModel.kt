package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsByIdsUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

data class WaitingRoomUiState(
    val room: OnlineRoom? = null,
    val isStarting: Boolean = false,
    val errorMessage: UiText? = null,
    val reactions: List<Reaction> = emptyList(),
    // Rough estimate of the in-progress round's total length (drawing +
    // guessing for every word), for a pendingNextRound joiner sitting in
    // the lobby — see room.startedAt and WaitingRoomScreen's countdown.
    // Null until the shared word list resolves to local Word objects.
    val estimatedRoundSeconds: Int? = null
)

@HiltViewModel
class WaitingRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val getWordsByIdsUseCase: GetWordsByIdsUseCase,
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository,
    botRoomEngine: BotRoomEngine
) : ViewModel() {

    private companion object {
        // Comfortably inside PRESENCE_TIMEOUT_MS, so a couple of missed beats
        // (a brief network blip) don't get anyone dropped.
        const val PRESENCE_HEARTBEAT_MS = 20_000L
    }

    val roomCode: String = checkNotNull(savedStateHandle["roomCode"])
    val myUid: String? get() = onlineGameRepository.currentUid

    /** How often each preset chat phrase has actually been sent from this device — see ReactionSendRow's usage-sorted "Bir şey söyle" sheet. */
    val phraseUsageCounts: StateFlow<Map<String, Int>> = settingsRepository.phraseUsageCounts

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
        // Presence heartbeat. Without it a lobby can't tell a player who
        // force-closed the app from one who's still sitting there — the stale
        // entry shows up as a phantom player AND, being permanently
        // un-ready, blocks the match from ever starting. See
        // OnlinePlayer.isPresent.
        viewModelScope.launch {
            while (true) {
                runCatching {
                    // Another device's cleanup may have pruned this one while
                    // it was offline; rejoining is friendlier than silently
                    // vanishing from a lobby the player is still looking at.
                    if (onlineGameRepository.isStillInRoom(roomCode)) {
                        onlineGameRepository.touchPresence(roomCode)
                    } else {
                        val nickname = settingsRepository.nickname.value.trim().ifBlank { "Oyuncu" }
                        onlineGameRepository.joinRoom(roomCode, nickname)
                    }
                }
                delay(PRESENCE_HEARTBEAT_MS)
            }
        }
        // Resolves the shared wordIds list to local Word objects (same ids,
        // same words on every device — see GetWordsByIdsUseCase) just to sum
        // up their drawing durations; only worth doing once per round, not
        // on every room snapshot, hence distinctUntilChanged on the ids.
        viewModelScope.launch {
            uiState.map { it.room?.wordIds ?: emptyList() }
                .distinctUntilChanged()
                .collect { wordIds ->
                    val estimate = if (wordIds.isEmpty()) {
                        null
                    } else {
                        runCatching { getWordsByIdsUseCase(wordIds) }.getOrDefault(emptyList())
                            .sumOf { GameConstants.drawingDurationSeconds(it.difficulty) + GameConstants.GUESS_DURATION_SECONDS }
                    }
                    _uiState.update { it.copy(estimatedRoundSeconds = estimate) }
                }
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
                _uiState.update { state -> state.copy(isStarting = false, errorMessage = UiText.of(R.string.error_game_start_failed)) }
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
