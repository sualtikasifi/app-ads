package com.sualtikasifi.cizimhafiza.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.domain.model.Achievement
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.usecase.GetStatisticsUseCase
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One achievement paired with whether this device has unlocked it. */
data class AchievementUiItem(val achievement: Achievement, val unlocked: Boolean)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    getStatisticsUseCase: GetStatisticsUseCase,
    settingsRepository: SettingsRepository,
    private val achievementDao: AchievementDao
) : ViewModel() {

    // Snapshot of which achievement ids were still unseen when this screen
    // opened — captured BEFORE markAllSeen() below clears the flag, so
    // StatisticsScreen knows exactly which cards to shimmer for 10s (see
    // AchievementDao.seen / the MainMenu badge that sent the player here).
    private val _newlyUnlockedIds = MutableStateFlow<Set<String>>(emptySet())
    val newlyUnlockedIds: StateFlow<Set<String>> = _newlyUnlockedIds.asStateFlow()

    init {
        viewModelScope.launch {
            _newlyUnlockedIds.value = achievementDao.getUnseenIds().toSet()
            achievementDao.markAllSeen()
        }
    }

    val statistics: StateFlow<GameStatistics> = getStatisticsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GameStatistics(emptyList(), 0, 0)
        )

    val playerProgress: StateFlow<LevelProgressState> = settingsRepository.lifetimeXp
        .map { LevelProgressState.forXp(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevelProgressState.forXp(0)
        )

    val achievements: StateFlow<List<AchievementUiItem>> = achievementDao.observeAll()
        .map { unlocked ->
            val unlockedIds = unlocked.map { it.id }.toSet()
            Achievement.entries.map { AchievementUiItem(it, unlocked = it.name in unlockedIds) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Achievement.entries.map { AchievementUiItem(it, unlocked = false) }
        )
}
