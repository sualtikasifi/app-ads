package com.sualtikasifi.cizimhafiza.presentation.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.model.WeeklyLeague
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class LeagueUiState(
    val table: LeagueTable? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeagueUiState())
    val uiState: StateFlow<LeagueUiState> = _uiState.asStateFlow()

    init {
        // The week may have rolled over since this device last opened the
        // app — refreshed here rather than by a scheduled worker, same
        // lazy-on-read reasoning as DailyChallengeRepository.refresh.
        settingsRepository.refreshWeeklyXp()

        // Publishes this device's own current standing the moment the table
        // is opened — the exact moment a stale score would actually be
        // noticed. Friends see it next time *they* open their own table
        // (eventually consistent, no live sync needed for a once-a-week
        // number nobody is staring at in real time).
        viewModelScope.launch {
            runCatching {
                val level = PlayerLevel.levelForXp(settingsRepository.lifetimeXp.value)
                val frameId = AvatarFrame.resolve(settingsRepository.selectedAvatarFrameId.value, level).name
                val nickname = settingsRepository.nickname.value.trim().ifBlank { "Oyuncu" }
                friendRepository.publishWeeklyScore(
                    nickname = nickname,
                    weeklyXp = settingsRepository.weeklyXp.value,
                    weekId = WeeklyLeague.weekIdFor(LocalDate.now().toEpochDay()),
                    level = level,
                    frameId = frameId
                )
            }
        }

        viewModelScope.launch {
            friendRepository.observeLeagueTable()
                .catch { } // a listener retry (see firestoreFlow) is invisible here; the last good table just stays on screen
                .collect { table -> _uiState.update { it.copy(table = table, isLoading = false) } }
        }
    }
}
