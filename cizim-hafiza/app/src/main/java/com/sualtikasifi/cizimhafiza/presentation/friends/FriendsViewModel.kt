package com.sualtikasifi.cizimhafiza.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.AddFriendOutcome
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.FriendRequest
import com.sualtikasifi.cizimhafiza.domain.model.GameMode
import com.sualtikasifi.cizimhafiza.domain.model.InviteEligibility
import com.sualtikasifi.cizimhafiza.domain.repository.BotFriendRequestPendingException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

// Incoming match invites are handled app-wide by IncomingInviteViewModel /
// IncomingInviteBanner (see NavGraph.kt) — not duplicated here, so there's
// only ever one live Firestore listener on observeIncomingInvites().
data class FriendsUiState(
    val nickname: String = "",
    val myFriendCode: String? = null,
    val friends: List<Friend> = emptyList(),
    val friendRequests: List<FriendRequest> = emptyList(),
    /** The request currently being accepted or declined — one at a time, so the row can show a spinner. */
    val answeringRequestUid: String? = null,
    val addFriendCodeInput: String = "",
    val isAddingFriend: Boolean = false,
    val invitingFriendUid: String? = null,
    val removingFriendUid: String? = null,
    val blockingFriendUid: String? = null,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val unblockingUid: String? = null,
    val confirmRemove: Friend? = null,
    val confirmBlock: Friend? = null,
    val errorMessage: UiText? = null,
    // Separate from errorMessage — shown in a neutral tone, not the error
    // color, since e.g. the bot's "request pending" response (see
    // BotFriendRequestPendingException) isn't actually a failure.
    val infoMessage: UiText? = null,
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

        // These all hit Firestore, which can fail (no network, security rules
        // not yet published, etc.) — left uncaught, that exception would
        // propagate out of the coroutine and crash the whole app instead of
        // just leaving this screen unable to load; surfacing it as
        // errorMessage keeps the crash from happening and tells the player
        // (and, while testing, us) what actually went wrong.
        viewModelScope.launch {
            runCatching { friendRepository.ensureFriendCode(nickname) }
                .onSuccess { code -> _uiState.update { it.copy(myFriendCode = code) } }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = UiText.of(R.string.error_friend_code_failed)) } }
        }
        viewModelScope.launch {
            friendRepository.observeFriends()
                .catch { error -> _uiState.update { it.copy(errorMessage = UiText.of(R.string.error_friend_list_failed)) } }
                .collect { friends -> _uiState.update { it.copy(friends = friends) } }
        }
        viewModelScope.launch {
            friendRepository.observeFriendRequests()
                .catch { _uiState.update { it.copy(errorMessage = UiText.of(R.string.error_friend_list_failed)) } }
                .collect { requests -> _uiState.update { it.copy(friendRequests = requests) } }
        }
        viewModelScope.launch {
            friendRepository.observeBlockedUsers()
                // Was silent: an unreachable blocked-users list rendered as
                // an empty one, so "I unblocked nobody but the list is
                // empty" and "this failed to load" looked identical.
                .catch { _uiState.update { it.copy(errorMessage = UiText.of(R.string.error_blocked_list_failed)) } }
                .collect { blocked -> _uiState.update { it.copy(blockedUsers = blocked) } }
        }
    }

    fun setNickname(name: String) = _uiState.update { it.copy(nickname = name, errorMessage = null) }

    fun setAddFriendCodeInput(code: String) {
        // Room/friend codes are always 6 digits — matches the keyboard shown on this field.
        _uiState.update { it.copy(addFriendCodeInput = code.filter(Char::isDigit).take(6), errorMessage = null, infoMessage = null) }
    }

    fun addFriend() {
        val state = _uiState.value
        if (state.addFriendCodeInput.length != 6 || state.isAddingFriend) return
        val nickname = state.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(isAddingFriend = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            friendRepository.addFriendByCode(state.addFriendCodeInput, nickname)
                .onSuccess { outcome ->
                    // A sent request is the normal outcome now, and it is not
                    // self-evident: the list does not change, so without a
                    // word here the screen looks like nothing happened.
                    val message = when (outcome) {
                        is AddFriendOutcome.RequestSent ->
                            UiText.of(R.string.info_friend_request_sent, outcome.nickname)
                        is AddFriendOutcome.Added ->
                            UiText.of(R.string.info_friend_added, outcome.friend.nickname)
                        is AddFriendOutcome.AlreadyFriends ->
                            UiText.of(R.string.info_friend_already, outcome.friend.nickname)
                    }
                    _uiState.update {
                        it.copy(isAddingFriend = false, addFriendCodeInput = "", infoMessage = message)
                    }
                    clearInfoMessageAfterDelay()
                }
                .onFailure { error ->
                    if (error is BotFriendRequestPendingException) {
                        _uiState.update {
                            it.copy(isAddingFriend = false, addFriendCodeInput = "", infoMessage = UiText.of(R.string.info_invite_sent))
                        }
                        clearInfoMessageAfterDelay()
                    } else {
                        _uiState.update { it.copy(isAddingFriend = false, errorMessage = UiText.of(R.string.error_friend_add_failed)) }
                    }
                }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        if (_uiState.value.answeringRequestUid != null) return
        val nickname = _uiState.value.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(answeringRequestUid = request.uid, errorMessage = null) }
        viewModelScope.launch {
            friendRepository.acceptFriendRequest(request, nickname)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            answeringRequestUid = null,
                            infoMessage = UiText.of(R.string.info_friend_added, request.nickname)
                        )
                    }
                    clearInfoMessageAfterDelay()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(answeringRequestUid = null, errorMessage = UiText.of(R.string.error_friend_add_failed))
                    }
                }
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        if (_uiState.value.answeringRequestUid != null) return
        _uiState.update { it.copy(answeringRequestUid = request.uid, errorMessage = null) }
        viewModelScope.launch {
            friendRepository.declineFriendRequest(request.uid)
            // Success and failure land the same way on purpose: the row
            // disappears either way (the listener drops it on success), and a
            // decline that failed is not something to make the player act on.
            _uiState.update { it.copy(answeringRequestUid = null) }
        }
    }

    /**
     * Info messages say something happened somewhere the list itself does not
     * show it, so they have to go away on their own — FriendsScreen fades
     * this out over the same window (see InfoMessageRow).
     */
    private fun clearInfoMessageAfterDelay() {
        viewModelScope.launch {
            delay(3_000)
            _uiState.update { if (it.infoMessage != null) it.copy(infoMessage = null) else it }
        }
    }

    /** Creates a quick room (default settings) and invites [friend] to it. */
    fun inviteFriend(friend: Friend) {
        if (_uiState.value.invitingFriendUid != null) return
        val nickname = _uiState.value.nickname.trim().ifBlank { "Oyuncu" }
        _uiState.update { it.copy(invitingFriendUid = friend.uid, errorMessage = null) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)

            // UX-only pre-check before even creating a room — the real
            // enforcement is firestore.rules' invites create rule, but
            // checking first gives a specific, friendly message instead of
            // creating a room the invite can never actually reach.
            when (val eligibility = friendRepository.canInvite(friend.uid)) {
                InviteEligibility.Blocked -> {
                    _uiState.update { it.copy(invitingFriendUid = null, errorMessage = UiText.of(R.string.error_invite_blocked)) }
                    return@launch
                }
                is InviteEligibility.OnCooldown -> {
                    val minutes = (eligibility.remainingMillis / 60_000L) + 1
                    _uiState.update { it.copy(invitingFriendUid = null, errorMessage = UiText.of(R.string.error_invite_cooldown, minutes)) }
                    return@launch
                }
                InviteEligibility.Eligible -> Unit
            }

            onlineGameRepository.createRoom(
                displayName = nickname,
                wordCount = GameConstants.WORD_COUNT_OPTIONS.first(),
                category = null,
                difficulty = null,
                mode = GameMode.NORMAL
            ).onSuccess { roomCode ->
                // The room itself already exists at this point regardless of
                // whether the invite notification succeeds — don't strand
                // the player on a failed send; they can still share the
                // room code directly.
                friendRepository.sendMatchInvite(friend.uid, roomCode, nickname)
                    .onSuccess {
                        _uiState.update { it.copy(invitingFriendUid = null, navigateToWaitingRoomCode = roomCode) }
                    }
                    .onFailure {
                        _uiState.update {
                            it.copy(
                                invitingFriendUid = null,
                                navigateToWaitingRoomCode = roomCode,
                                errorMessage = UiText.of(R.string.error_invite_failed_room_ready)
                            )
                        }
                    }
            }.onFailure { error ->
                _uiState.update { it.copy(invitingFriendUid = null, errorMessage = UiText.of(R.string.error_invite_send_failed)) }
            }
        }
    }

    fun onNavigatedToWaitingRoom() = _uiState.update { it.copy(navigateToWaitingRoomCode = null) }

    fun confirmRemoveFriend(friend: Friend) = _uiState.update { it.copy(confirmRemove = friend) }
    fun dismissRemoveConfirm() = _uiState.update { it.copy(confirmRemove = null) }

    fun removeFriend(friend: Friend) {
        _uiState.update { it.copy(confirmRemove = null, removingFriendUid = friend.uid, errorMessage = null) }
        viewModelScope.launch {
            friendRepository.removeFriend(friend.uid)
                .onSuccess { _uiState.update { it.copy(removingFriendUid = null) } }
                .onFailure { error ->
                    _uiState.update { it.copy(removingFriendUid = null, errorMessage = UiText.of(R.string.error_friend_remove_failed)) }
                }
        }
    }

    fun confirmBlockFriend(friend: Friend) = _uiState.update { it.copy(confirmBlock = friend) }
    fun dismissBlockConfirm() = _uiState.update { it.copy(confirmBlock = null) }

    /** Blocking someone doesn't unfriend them — it only stops future invites from them (see firestore.rules). */
    fun blockFriend(friend: Friend) {
        _uiState.update { it.copy(confirmBlock = null, blockingFriendUid = friend.uid, errorMessage = null) }
        viewModelScope.launch {
            friendRepository.blockUser(friend.uid, friend.nickname)
                .onSuccess { _uiState.update { it.copy(blockingFriendUid = null) } }
                .onFailure { error ->
                    _uiState.update { it.copy(blockingFriendUid = null, errorMessage = UiText.of(R.string.error_block_failed)) }
                }
        }
    }

    fun unblockUser(blocked: BlockedUser) {
        _uiState.update { it.copy(unblockingUid = blocked.uid, errorMessage = null) }
        viewModelScope.launch {
            friendRepository.unblockUser(blocked.uid)
                .onSuccess { _uiState.update { it.copy(unblockingUid = null) } }
                .onFailure { error ->
                    _uiState.update { it.copy(unblockingUid = null, errorMessage = UiText.of(R.string.error_unblock_failed)) }
                }
        }
    }
}
