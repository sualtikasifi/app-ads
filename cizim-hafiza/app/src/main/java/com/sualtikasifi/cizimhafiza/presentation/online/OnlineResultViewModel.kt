package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.OnlineRoom
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsForGameUseCase
import com.sualtikasifi.cizimhafiza.domain.usecase.SaveOnlineGameSessionUseCase
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnlineResultUiState(
    val room: OnlineRoom? = null,
    val itemsByUid: Map<String, List<ResultItem>> = emptyMap(),
    val selectedUid: String? = null,
    val isLoadingItems: Boolean = true,
    val rematchRequested: Boolean = false,
    val navigateToRematchRoomCode: String? = null,
    val reactions: List<Reaction> = emptyList()
)

@HiltViewModel
class OnlineResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineGameRepository: OnlineGameRepository,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val saveOnlineGameSessionUseCase: SaveOnlineGameSessionUseCase,
    botRoomEngine: BotRoomEngine
) : ViewModel() {

    val roomCode: String = checkNotNull(savedStateHandle[Screen.ArgRoomCode])
    val myUid: String? get() = onlineGameRepository.currentUid

    private val _uiState = MutableStateFlow(OnlineResultUiState())
    val uiState: StateFlow<OnlineResultUiState> = _uiState.asStateFlow()

    private var lastKnownWordIds: List<Int>? = null
    private var hasTriggeredRematchReset = false
    private var hasLoadedItems = false

    init {
        // Harmless/no-op for any other room — only ever drives room 130246
        // (see BotRoomEngine) and only starts its listener once per process.
        botRoomEngine.ensureRunning()
        // Both Flows close with an exception on a Firestore listener error
        // (see OnlineGameRepositoryImpl.observeRoom/observeReactions) —
        // .catch{} keeps that from crashing the app; the screen just stops
        // updating until the listener recovers, same as a brief network drop.
        viewModelScope.launch {
            onlineGameRepository.observeRoom(roomCode).catch { }.collect { room ->
                _uiState.update { it.copy(room = room) }
                if (room == null) return@collect

                val previousWordIds = lastKnownWordIds
                // A rematch was reset elsewhere (by the host): the room went
                // back to PLAYING with a fresh word list — jump back into a
                // new match instead of staying on this finished-round screen.
                if (room.status == RoomStatus.PLAYING &&
                    previousWordIds != null &&
                    room.wordIds != previousWordIds
                ) {
                    _uiState.update { it.copy(navigateToRematchRoomCode = roomCode) }
                    return@collect
                }
                lastKnownWordIds = room.wordIds

                if (!hasLoadedItems && room.players.size >= 2 && room.players.all { it.finished }) {
                    hasLoadedItems = true
                    loadItems(room, myUid)
                }

                maybeTriggerRematchReset(room)
            }
        }
        viewModelScope.launch {
            onlineGameRepository.observeReactions(roomCode).catch { }.collect { reactions ->
                _uiState.update { it.copy(reactions = reactions) }
            }
        }
    }

    private fun loadItems(room: OnlineRoom, myUidLocal: String?) {
        val me = room.players.find { it.uid == myUidLocal }
        viewModelScope.launch {
            // A network failure fetching any player's drawings must not
            // crash the app right as the match concludes, nor leave
            // isLoadingItems stuck true forever — fall back to an empty
            // gallery for whichever player's fetch failed (scores still
            // come from `room`, so the result screen stays useful).
            val itemsByUid = runCatching {
                coroutineScope {
                    room.players
                        .map { player -> player.uid to async { onlineGameRepository.getPlayerResultItems(roomCode, player.uid) } }
                        .associate { (uid, deferred) -> uid to (runCatching { deferred.await() }.getOrDefault(emptyList())) }
                }
            }.getOrDefault(emptyMap())
            _uiState.update { it.copy(itemsByUid = itemsByUid, selectedUid = myUidLocal, isLoadingItems = false) }

            // Recorded once per finished round (loadItems only ever runs
            // once per ViewModel instance, guarded by hasLoadedItems — a
            // rematch gets a brand new OnlineResultViewModel next round).
            if (me != null) {
                val ranked = room.players.sortedByDescending { it.totalScore }
                val placement = ranked.indexOfFirst { it.uid == myUidLocal } + 1
                saveOnlineGameSessionUseCase(
                    totalScore = me.totalScore,
                    wordCount = room.wordCount,
                    correctCount = me.correctCount,
                    fastestCorrectMs = me.fastestCorrectMs,
                    placement = placement,
                    playerCount = room.players.size
                )
            }
        }
    }

    fun selectPlayer(uid: String) {
        _uiState.update { it.copy(selectedUid = uid) }
    }

    // Either player can trigger this — not just the host — so a rematch
    // isn't stuck forever if the host happened to leave this screen first.
    // resetForRematch() is a Firestore transaction, so if both clients race
    // to call it at once only one actually applies.
    private fun maybeTriggerRematchReset(room: OnlineRoom) {
        if (hasTriggeredRematchReset) return
        if (room.status != RoomStatus.FINISHED) return
        if (room.players.size < 2) return
        // Require every currently-listed player to vote yes — the direct
        // generalization of the old "both players vote" rule.
        if (room.rematchVotes.size < room.players.size) return
        hasTriggeredRematchReset = true
        viewModelScope.launch {
            // Fire-and-forget: a failure here just means the rematch reset
            // doesn't happen yet — the other client racing to call the same
            // transaction (see the class doc above) can still succeed, and
            // this ViewModel's own room observer will retry via the next
            // room update either way. Must not crash on a network blip.
            runCatching {
                val words = getWordsForGameUseCase(room.wordCount, room.category, room.difficulty)
                onlineGameRepository.resetForRematch(roomCode, words.map { it.id })
            }
        }
    }

    fun requestRematch() {
        if (_uiState.value.rematchRequested) return
        _uiState.update { it.copy(rematchRequested = true) }
        viewModelScope.launch { runCatching { onlineGameRepository.voteRematch(roomCode) } }
    }

    fun sendReaction(emoji: String, messageKey: String) {
        viewModelScope.launch { runCatching { onlineGameRepository.sendReaction(roomCode, emoji, messageKey) } }
    }
}
