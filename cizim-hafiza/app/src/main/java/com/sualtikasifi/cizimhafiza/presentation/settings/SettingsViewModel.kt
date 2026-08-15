package com.sualtikasifi.cizimhafiza.presentation.settings

import androidx.lifecycle.ViewModel
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled
    val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled

    fun setSoundEnabled(enabled: Boolean) = settingsRepository.setSoundEnabled(enabled)
    fun setVibrationEnabled(enabled: Boolean) = settingsRepository.setVibrationEnabled(enabled)
}
