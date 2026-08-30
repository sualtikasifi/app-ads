package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomingInviteUiState(
    val invite: MatchInvite? = null,
    val isResponding: Boolean = false,
    val navigateToWaitingRoomCode: String? = null
)

/**
 * Owns the app-wide "someone invited you to a match" banner — a single
 * instance is created once, scoped to CizimHafizaNavGraph (see hiltViewModel()
 * there), so it keeps listening for invites and can show the banner over
 * whatever screen the player is currently on, not just while the Friends
 * screen is open.
 */
@HiltViewModel
class IncomingInviteViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingInviteUiState())
    val uiState: StateFlow<IncomingInviteUiState> = _uiState.asStateFlow()

    private var pendingInvites: List<MatchInvite> = emptyList()

    init {
        viewModelScope.launch {
            friendRepository.observeIncomingInvites()
                // A banner that can't load shouldn't itself crash the app —
                // fail silently and just show nothing until it recovers.
                .catch { }
                .collect { invites ->
                    pendingInvites = invites
                    _uiState.update { state ->
                        // Only swap in a different invite once the one currently
                        // shown (if any) is gone — otherwise a list update mid
                        // accept/decline would yank the banner out from under
                        // the player's tap.
                        val stillPending = state.invite != null && invites.any { it.id == state.invite.id }
                        if (stillPending) state else state.copy(invite = invites.firstOrNull())
                    }
                }
        }
    }

    fun accept() {
        val invite = _uiState.value.invite ?: return
        if (_uiState.value.isResponding) return
        val nickname = settingsRepository.nickname.value.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isResponding = true) }
        viewModelScope.launch {
            val result = onlineGameRepository.joinRoom(invite.roomCode, nickname)
            // Best-effort cleanup — must not crash on a network blip right
            // between successfully joining and reporting that back below.
            runCatching { friendRepository.consumeInvite(invite.id) }
            result.onSuccess {
                _uiState.update { it.copy(isResponding = false, invite = null, navigateToWaitingRoomCode = invite.roomCode) }
            }.onFailure {
                _uiState.update { it.copy(isResponding = false, invite = null) }
            }
        }
    }

    fun decline() {
        val invite = _uiState.value.invite ?: return
        _uiState.update { it.copy(invite = null) }
        // declineInvite (not consumeInvite) also starts a cooldown so this
        // sender can't immediately re-invite — see FriendRepository.
        viewModelScope.launch { friendRepository.declineInvite(invite) }
    }

    fun onNavigatedToWaitingRoom() = _uiState.update { it.copy(navigateToWaitingRoomCode = null) }
}
