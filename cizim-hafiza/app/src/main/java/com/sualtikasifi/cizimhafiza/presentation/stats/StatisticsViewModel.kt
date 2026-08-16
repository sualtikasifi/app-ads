package com.sualtikasifi.cizimhafiza.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.GameStatistics
import com.sualtikasifi.cizimhafiza.domain.model.PlayerProgress
import com.sualtikasifi.cizimhafiza.domain.usecase.GetStatisticsUseCase
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    getStatisticsUseCase: GetStatisticsUseCase,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val statistics: StateFlow<GameStatistics> = getStatisticsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GameStatistics(emptyList(), 0, 0)
        )

    val playerProgress: StateFlow<PlayerProgress> = settingsRepository.lifetimeScore
        .map { PlayerProgress.forScore(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerProgress.forScore(0)
        )
}
