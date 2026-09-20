package com.sualtikasifi.cizimhafiza.presentation.chests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.Chest
import com.sualtikasifi.cizimhafiza.domain.model.ChestReward
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Kasalarım" screen: the 4 chest slots (see SettingsRepository.
 * chestSlots), earned only from a won Arkadaşla Yarış match, and the gold
 * they pay out.
 */
@HiltViewModel
class ChestsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val chestSlots: StateFlow<List<Chest?>> = settingsRepository.chestSlots
    val goldBalance: StateFlow<Int> = settingsRepository.goldBalance

    // Every countdown on screen reads against this rather than calling
    // System.currentTimeMillis() directly in the composable — one ticking
    // clock the whole slot row recomposes from together, instead of each
    // card free-running its own.
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    /** Non-null exactly once, right after a chest is opened — the reveal dialog reads and clears it. */
    private val _lastReward = MutableStateFlow<ChestReward?>(null)
    val lastReward: StateFlow<ChestReward?> = _lastReward.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _nowMillis.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    fun startUnlocking(chestId: String) {
        settingsRepository.startUnlockingChest(chestId)
    }

    fun open(chestId: String) {
        settingsRepository.openChestIfReady(chestId)?.let { _lastReward.value = it }
    }

    fun consumeLastReward() {
        _lastReward.value = null
    }
}
