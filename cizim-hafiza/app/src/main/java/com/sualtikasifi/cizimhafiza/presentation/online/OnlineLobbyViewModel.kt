package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exists for one number: how many friend requests are waiting.
 *
 * A request only ever appears inside Arkadaşlarım, so without a count on the
 * way in there is nothing at all to tell the recipient one arrived — they
 * would have to go looking on a hunch. This is the last screen before that
 * one, which makes it the right place and a cheap one: the listener lives
 * only while this screen is open, and an empty collection costs no document
 * reads to watch.
 */
@HiltViewModel
class OnlineLobbyViewModel @Inject constructor(
    friendRepository: FriendRepository
) : ViewModel() {

    private val _pendingFriendRequests = MutableStateFlow(0)
    val pendingFriendRequests: StateFlow<Int> = _pendingFriendRequests.asStateFlow()

    init {
        viewModelScope.launch {
            friendRepository.observeFriendRequests()
                // No badge is the honest fallback for a count that could not
                // be loaded — a screen this simple should not grow an error.
                .catch { }
                .collect { requests -> _pendingFriendRequests.value = requests.size }
        }
    }
}
