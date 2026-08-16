package com.sualtikasifi.cizimhafiza.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val nickname: String = "",
    val myFriendCode: String? = null,
    val friends: List<Friend> = emptyList(),
    val incomingInvites: List<MatchInvite> = emptyList(),
    val addFriendCodeInput: String = "",
    val isAddingFriend: Boolean = false,
    val invitingFriendUid: String? = null,
    val acceptingInviteId: String? = null,
    val errorMessage: String? = null,
    val navigateToWaitingRoomCode: String? = null
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val onlineGameRepository: OnlineGameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        val nickname = settingsRepository.nickname.value.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(nickname = nickname) }

        viewModelScope.launch {
            val code = friendRepository.ensureFriendCode(nickname)
            _uiState.update { it.copy(myFriendCode = code) }
        }
        viewModelScope.launch {
            friendRepository.observeFriends().collect { friends ->
                _uiState.update { it.copy(friends = friends) }
            }
        }
        viewModelScope.launch {
            friendRepository.observeIncomingInvites().collect { invites ->
                _uiState.update { it.copy(incomingInvites = invites) }
            }
        }
    }

    fun setNickname(name: String) = _uiState.update { it.copy(nickname = name, errorMessage = null) }

    fun setAddFriendCodeInput(code: String) {
        // Room/friend codes are always 6 digits — matches the keyboard shown on this field.
        _uiState.update { it.copy(addFriendCodeInput = code.filter(Char::isDigit).take(6), errorMessage = null) }
    }

    fun addFriend() {
        val state = _uiState.value
        if (state.addFriendCodeInput.length != 6 || state.isAddingFriend) return
        val nickname = state.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isAddingFriend = true, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            friendRepository.addFriendByCode(state.addFriendCodeInput, nickname)
                .onSuccess {
                    _uiState.update { it.copy(isAddingFriend = false, addFriendCodeInput = "") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isAddingFriend = false, errorMessage = error.message ?: "Arkadaş eklenemedi") }
                }
        }
    }

    /** Creates a quick room (default settings) and invites [friend] to it. */
    fun inviteFriend(friend: Friend) {
        if (_uiState.value.invitingFriendUid != null) return
        val nickname = _uiState.value.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(invitingFriendUid = friend.uid, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            onlineGameRepository.createRoom(
                displayName = nickname,
                wordCount = GameConstants.WORD_COUNT_OPTIONS.first(),
                category = null,
                difficulty = null,
                mode = GameMode.NORMAL
            ).onSuccess { roomCode ->
                friendRepository.sendMatchInvite(friend.uid, roomCode, nickname)
                _uiState.update { it.copy(invitingFriendUid = null, navigateToWaitingRoomCode = roomCode) }
            }.onFailure { error ->
                _uiState.update { it.copy(invitingFriendUid = null, errorMessage = error.message ?: "Davet gönderilemedi") }
            }
        }
    }

    fun acceptInvite(invite: MatchInvite) {
        if (_uiState.value.acceptingInviteId != null) return
        val nickname = _uiState.value.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(acceptingInviteId = invite.id, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            val result = onlineGameRepository.joinRoom(invite.roomCode, nickname)
            friendRepository.consumeInvite(invite.id)
            result.onSuccess {
                _uiState.update { it.copy(acceptingInviteId = null, navigateToWaitingRoomCode = invite.roomCode) }
            }.onFailure { error ->
                _uiState.update { it.copy(acceptingInviteId = null, errorMessage = error.message ?: "Odaya katılamadın") }
            }
        }
    }

    fun dismissInvite(invite: MatchInvite) {
        viewModelScope.launch { friendRepository.consumeInvite(invite.id) }
    }

    fun onNavigatedToWaitingRoom() = _uiState.update { it.copy(navigateToWaitingRoomCode = null) }
}
