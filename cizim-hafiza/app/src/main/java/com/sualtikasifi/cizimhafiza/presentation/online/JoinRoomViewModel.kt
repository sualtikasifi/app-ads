package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.RoomStatus
import com.sualtikasifi.cizimhafiza.domain.repository.OnlineGameRepository
import com.sualtikasifi.cizimhafiza.domain.repository.RoomAlreadyStartedException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomFullException
import com.sualtikasifi.cizimhafiza.domain.repository.RoomNotFoundException
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class JoinRoomUiState(
    val nickname: String = "",
    val roomCode: String = "",
    val isJoining: Boolean = false,
    val errorMessage: String? = null,
    // Non-null while a match is already in progress in the room being
    // joined and this screen is auto-retrying once it frees up — only ever
    // set for the bot room (130246), the one room whose lifecycle is
    // guaranteed to cycle back to WAITING on its own (see BotRoomEngine).
    // A normal friend room never re-opens to new joiners after it starts,
    // so this path isn't offered there — see friendlyMessage's
    // RoomAlreadyStartedException case for that unchanged behavior.
    val waitingRoomCode: String? = null,
    val waitingSeconds: Int = 0
)

private const val WAITING_GIVE_UP_SECONDS = 240

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

    private var waitingJob: Job? = null

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
        waitingJob?.cancel()
        _uiState.update { it.copy(isJoining = true, errorMessage = null, waitingRoomCode = null, waitingSeconds = 0) }
        viewModelScope.launch {
            settingsRepository.setNickname(nickname)
            // The bot room (see BotRoomEngine) has no real owner to have
            // created it ahead of time — bootstrap/repair it here before the
            // normal join call below, which would otherwise 404 on a room
            // that's never existed yet or is stuck from a past match.
            if (state.roomCode == BotRoomEngine.ROOM_CODE) {
                runCatching { botRoomEngine.ensureBootstrapped() }
            }
            attemptJoin(state.roomCode, nickname, onJoined)
        }
    }

    /** Cancels an active wait-for-room-to-free-up loop, back to the plain form. */
    fun cancelWaiting() {
        waitingJob?.cancel()
        _uiState.update { it.copy(waitingRoomCode = null, waitingSeconds = 0) }
    }

    private suspend fun attemptJoin(roomCode: String, nickname: String, onJoined: (String) -> Unit) {
        onlineGameRepository.joinRoom(roomCode, nickname)
            .onSuccess {
                botRoomEngine.ensureRunning()
                _uiState.update { it.copy(isJoining = false, waitingRoomCode = null) }
                onJoined(roomCode)
            }
            .onFailure { error ->
                if (error is RoomAlreadyStartedException && roomCode == BotRoomEngine.ROOM_CODE) {
                    startWaiting(roomCode, nickname, onJoined)
                } else {
                    _uiState.update { it.copy(isJoining = false, errorMessage = friendlyMessage(error)) }
                }
            }
    }

    // Bot room only (see JoinRoomUiState.waitingRoomCode) — the match
    // currently in progress there always ends and the room cycles back to
    // WAITING on its own (BotRoomEngine's maintenance), so instead of a
    // dead-end "oyun zaten başladı" error, this waits and auto-joins the
    // moment it reopens, showing a live elapsed-time counter in the
    // meantime. Gives up after WAITING_GIVE_UP_SECONDS as a safety net in
    // case that maintenance is ever slower than expected.
    private fun startWaiting(roomCode: String, nickname: String, onJoined: (String) -> Unit) {
        _uiState.update { it.copy(isJoining = false, waitingRoomCode = roomCode, waitingSeconds = 0) }
        waitingJob = viewModelScope.launch {
            // A real once-a-second display tick, independent of how often
            // Firestore happens to emit — cancelled together with the wait
            // below (same parent job) once it resolves either way.
            val tickerJob = launch {
                while (isActive) {
                    delay(1_000)
                    _uiState.update { it.copy(waitingSeconds = it.waitingSeconds + 1) }
                }
            }
            val becameAvailable = withTimeoutOrNull(WAITING_GIVE_UP_SECONDS * 1_000L) {
                onlineGameRepository.observeRoom(roomCode)
                    .catch { }
                    .filterNotNull()
                    .first { it.status == RoomStatus.WAITING }
            } != null
            tickerJob.cancel()

            if (becameAvailable) {
                attemptJoin(roomCode, nickname, onJoined)
            } else {
                _uiState.update {
                    it.copy(waitingRoomCode = null, waitingSeconds = 0, errorMessage = "Oda şu an çok yoğun, birazdan tekrar dene")
                }
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
