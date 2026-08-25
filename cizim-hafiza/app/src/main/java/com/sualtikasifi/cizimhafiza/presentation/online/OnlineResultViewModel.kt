package com.sualtikasifi.cizimhafiza.presentation.online

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.Achievement
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
    // Set once someone joins mid-round: instead of a normal vote-based
    // rematch, the whole group is about to be forced back to the lobby
    // (see maybeTriggerRematchReset) — the Screen disables "Tekrar Oyna"
    // and shows a message instead while this is true.
    val navigateToWaitingRoomCode: String? = null,
    val rematchBlockedByNewJoiner: Boolean = false,
    val reactions: List<Reaction> = emptyList(),
    val newlyUnlockedAchievements: List<Achievement> = emptyList()
)

@HiltViewModel
class OnlineResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineGameRepository: OnlineGameRepository,
    private val getWordsForGameUseCase: GetWordsForGameUseCase,
    private val saveOnlineGameSessionUseCase: SaveOnlineGameSessionUseCase,
    private val adManager: AdManager,
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

                // Forced back to the lobby elsewhere (a joiner appeared
                // mid-round — see maybeTriggerRematchReset/
                // returnToWaitingRoom): whichever device's transaction won
                // the race, every device on this screen reacts the same way
                // once it observes the resulting WAITING status.
                if (room.status == RoomStatus.WAITING) {
                    _uiState.update { it.copy(navigateToWaitingRoomCode = roomCode) }
                    return@collect
                }

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

                // pendingNextRound players (joined mid-round) never finish
                // THIS round, and a player who quit (left=true) never will
                // either — both excluded here the same way submitResult()
                // excludes them when deciding the room is FINISHED. Without
                // the left check, a departed player's finished=false entry
                // would keep this screen's "everyone's done" condition (and
                // the rematch-vote count below) permanently unsatisfiable.
                val activePlayers = room.players.filterNot { it.pendingNextRound || it.left }
                if (!hasLoadedItems && activePlayers.size >= 2 && activePlayers.all { it.finished }) {
                    hasLoadedItems = true
                    loadItems(room, myUid)
                }

                _uiState.update { it.copy(rematchBlockedByNewJoiner = room.players.any { p -> p.pendingNextRound }) }
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
            //
            // Ranked over the players who actually PLAYED this round, not
            // room.players: a pendingNextRound joiner sat the round out in the
            // lobby and still carries a fresh totalScore of 0, so counting
            // them would inflate playerCount and hand the player a better
            // placement than they earned ("2nd of 3" for a two-player round).
            if (me != null) {
                val roundPlayers = room.players.filterNot { it.pendingNextRound || it.left }
                val ranked = roundPlayers.sortedByDescending { it.totalScore }
                val placement = ranked.indexOfFirst { it.uid == myUidLocal } + 1
                if (placement > 0) {
                    val newlyUnlocked = saveOnlineGameSessionUseCase(
                        totalScore = me.totalScore,
                        wordCount = room.wordCount,
                        correctCount = me.correctCount,
                        fastestCorrectMs = me.fastestCorrectMs,
                        placement = placement,
                        playerCount = roundPlayers.size
                    )
                    _uiState.update { it.copy(newlyUnlockedAchievements = newlyUnlocked) }
                }
            }
        }
    }

    /** Called once when the finished-round comparison is actually showing — see AdManager's placement doc. */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        adManager.maybeShowInterstitial(activity, onDismissed)
    }

    fun selectPlayer(uid: String) {
        _uiState.update { it.copy(selectedUid = uid) }
    }

    // Either player can trigger this — not just the host — so a rematch
    // isn't stuck forever if the host happened to leave this screen first.
    // resetForRematch()/returnToWaitingRoom() are Firestore transactions,
    // so if multiple clients race to call one at once only one applies.
    private fun maybeTriggerRematchReset(room: OnlineRoom) {
        if (hasTriggeredRematchReset) return
        if (room.status != RoomStatus.FINISHED) return
        val activePlayers = room.players.filterNot { it.pendingNextRound || it.left }
        if (activePlayers.size < 2) return

        // Someone joined mid-round: skip the normal vote entirely and force
        // everyone back to a fresh lobby together instead of an instant
        // rematch — see OnlineGameRepository.returnToWaitingRoom. Not
        // gated on this call's own success: if another client's own
        // transaction wins the race instead, this device's room observer
        // (see the collect block above) reacts to the resulting WAITING
        // status the same way regardless of which device caused it.
        if (room.players.any { it.pendingNextRound }) {
            hasTriggeredRematchReset = true
            viewModelScope.launch { runCatching { onlineGameRepository.returnToWaitingRoom(roomCode) } }
            return
        }

        // Require every currently-listed player to vote yes — the direct
        // generalization of the old "both players vote" rule.
        if (room.rematchVotes.size < activePlayers.size) return
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
        if (_uiState.value.rematchRequested || _uiState.value.rematchBlockedByNewJoiner) return
        _uiState.update { it.copy(rematchRequested = true) }
        viewModelScope.launch { runCatching { onlineGameRepository.voteRematch(roomCode) } }
    }

    fun sendReaction(emoji: String, messageKey: String) {
        viewModelScope.launch { runCatching { onlineGameRepository.sendReaction(roomCode, emoji, messageKey) } }
    }
}
