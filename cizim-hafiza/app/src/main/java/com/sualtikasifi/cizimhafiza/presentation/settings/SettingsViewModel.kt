package com.sualtikasifi.cizimhafiza.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import com.sualtikasifi.cizimhafiza.data.local.WordPoolSynchronizer
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wordPoolSynchronizer: WordPoolSynchronizer
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled
    val vibrationEnabled: StateFlow<Boolean> = settingsRepository.vibrationEnabled
    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.notificationsEnabled

    // AppCompatDelegate.getApplicationLocales() is empty until the user has
    // manually picked a language once — in that case the current UI language
    // is whatever the system resolved (see MainActivity's AppCompat theme),
    // which "tr"/"en" both fall back to correctly here since "tr" is this
    // app's own default resource set.
    private val _language = MutableStateFlow(
        AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "tr"
    )
    val language: StateFlow<String> = _language.asStateFlow()

    fun setSoundEnabled(enabled: Boolean) = settingsRepository.setSoundEnabled(enabled)
    fun setVibrationEnabled(enabled: Boolean) = settingsRepository.setVibrationEnabled(enabled)
    fun setNotificationsEnabled(enabled: Boolean) = settingsRepository.setNotificationsEnabled(enabled)

    fun setLanguage(languageTag: String) {
        if (_language.value == languageTag) return
        _language.value = languageTag
        // Triggers an Activity recreation (AppCompatActivity applies the new
        // locale immediately) — the word pool resync below runs on the
        // synchronizer's own CoroutineScope so it isn't cancelled by that.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        wordPoolSynchronizer.syncAsync()
    }
}
