package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    achievementDao: AchievementDao
) : ViewModel() {

    // Drives the small badge on the "İstatistikler" tile — cleared the next
    // time StatisticsScreen opens (see StatisticsViewModel.markAllSeen).
    val hasUnseenAchievement: StateFlow<Boolean> = achievementDao.observeUnseenCount()
        .map { it > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
}
