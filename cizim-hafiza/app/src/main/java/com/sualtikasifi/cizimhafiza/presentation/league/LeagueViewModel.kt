package com.sualtikasifi.cizimhafiza.presentation.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import com.sualtikasifi.cizimhafiza.util.WeeklyScorePublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeagueUiState(
    val table: LeagueTable? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val settingsRepository: SettingsRepository,
    private val weeklyScorePublisher: WeeklyScorePublisher
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeagueUiState())
    val uiState: StateFlow<LeagueUiState> = _uiState.asStateFlow()

    init {
        // The week may have rolled over since this device last opened the
        // app — refreshed here rather than by a scheduled worker, same
        // lazy-on-read reasoning as DailyChallengeRepository.refresh.
        settingsRepository.refreshWeeklyXp()

        // A publish is already following this device's XP (see
        // WeeklyScorePublisher, started in CizimHafizaApp) — this only asks
        // it not to wait out its debounce, so a table opened seconds after a
        // match does not show a stale row for the player looking at it.
        // Friends see the update next time their own table loads;
        // eventually consistent is fine for a weekly number.
        weeklyScorePublisher.publishNow()

        viewModelScope.launch {
            friendRepository.observeLeagueTable()
                .catch { } // a listener retry (see firestoreFlow) is invisible here; the last good table just stays on screen
                .collect { table -> _uiState.update { it.copy(table = table, isLoading = false) } }
        }
    }
}
