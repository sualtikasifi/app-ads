package com.sualtikasifi.cizimhafiza.presentation.duel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Duel
import com.sualtikasifi.cizimhafiza.domain.repository.DuelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DuelListUiState(
    val incoming: List<Duel> = emptyList(),
    val sent: List<Duel> = emptyList()
)

@HiltViewModel
class DuelListViewModel @Inject constructor(
    private val duelRepository: DuelRepository
) : ViewModel() {

    val myUid: String? get() = duelRepository.currentUid

    val uiState: StateFlow<DuelListUiState> = combine(
        duelRepository.observeIncomingDuels().catch { emit(emptyList()) },
        duelRepository.observeSentDuels().catch { emit(emptyList()) }
    ) { incoming, sent -> DuelListUiState(incoming = incoming, sent = sent) }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = DuelListUiState())

    /** Marks a completed sent duel as seen — call when its result card is opened. */
    fun markSeen(duelId: String) {
        viewModelScope.launch { runCatching { duelRepository.markSeenByChallenger(duelId) } }
    }

    fun deleteDuel(duelId: String) {
        viewModelScope.launch { runCatching { duelRepository.deleteDuel(duelId) } }
    }
}
