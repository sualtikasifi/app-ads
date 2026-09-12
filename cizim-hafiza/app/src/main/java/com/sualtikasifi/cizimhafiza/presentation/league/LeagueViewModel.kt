package com.sualtikasifi.cizimhafiza.presentation.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.GlobalLeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.LeagueReward
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.GlobalLeagueRepository
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

/** The two tables the screen switches between — see [LeagueUiState]. */
enum class LeagueTab { Friends, Global }

data class LeagueUiState(
    val tab: LeagueTab = LeagueTab.Friends,
    val table: LeagueTable? = null,
    val isLoading: Boolean = true,
    val global: GlobalLeagueTable? = null,
    val globalLoading: Boolean = false,
    val globalFailed: Boolean = false,
    /**
     * A prize collected on THIS visit, shown once and then dismissed.
     *
     * Distinct from simply having won: the published table carries last
     * week's winners for the whole week, so it is read again on every open.
     * Only the first read actually grants anything (see
     * SettingsRepository.grantLeagueReward), and only that read should
     * celebrate.
     */
    val justWon: LeagueReward? = null
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val globalLeagueRepository: GlobalLeagueRepository,
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

        // Loaded even though the friends tab opens first: this is the read
        // that hands over a prize won last week, and a player who never
        // switches tabs should still collect it.
        loadGlobal()
    }

    fun selectTab(tab: LeagueTab) {
        _uiState.update { it.copy(tab = tab) }
        // Only if the first attempt failed — the published table is rebuilt
        // every six hours and the repository caches it, so switching tabs is
        // otherwise free.
        if (tab == LeagueTab.Global && _uiState.value.globalFailed) loadGlobal()
    }

    fun refreshGlobal() = loadGlobal(force = true)

    fun dismissPrize() = _uiState.update { it.copy(justWon = null) }

    private fun loadGlobal(force: Boolean = false) {
        if (_uiState.value.globalLoading) return
        _uiState.update { it.copy(globalLoading = true, globalFailed = false) }
        viewModelScope.launch {
            globalLeagueRepository.table(forceRefresh = force)
                .onSuccess { table ->
                    _uiState.update {
                        it.copy(
                            global = table,
                            globalLoading = false,
                            justWon = it.justWon ?: collectPrize(table)
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(globalLoading = false, globalFailed = true) }
                }
        }
    }

    /**
     * Grants last week's prize if this device won one and has not already
     * been given it, and returns it only when something was actually
     * granted — so the celebration fires once rather than on every open.
     *
     * Granted from the published table because the app was going to read it
     * anyway: a separate per-player award document would be a second read
     * for a prize almost nobody has won.
     */
    private fun collectPrize(table: GlobalLeagueTable): LeagueReward? {
        if (table.myLastWeekWin == null) return null
        val rewardId = table.lastWeek?.rewardId ?: return null
        if (!settingsRepository.grantLeagueReward(rewardId)) return null
        // Null when this build does not know the id — an older app reading a
        // prize whose artwork it does not ship. The grant still stands, so
        // updating the app later reveals it.
        return LeagueReward.find(rewardId)
    }
}
