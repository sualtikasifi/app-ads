package com.sualtikasifi.cizimhafiza.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.data.local.WordPoolSynchronizer
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wordPoolSynchronizer: WordPoolSynchronizer,
    authRepository: AuthRepository
) : ViewModel() {

    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled

    // A small nudge on the "Hesap" row rather than an interruption: a
    // player who has played enough to have something worth protecting, but
    // never opened this screen's Google-link offer on their own, is exactly
    // who loses everything on a lost or reset phone. Disappears the moment
    // they link, since the condition is just "still anonymous".
    val showAccountNudge: StateFlow<Boolean> = authRepository.authState
        .map { it !is AuthState.Linked && settingsRepository.lifetimeGamesPlayed >= ACCOUNT_NUDGE_GAMES_THRESHOLD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)
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
    val musicEnabled: StateFlow<Boolean> = settingsRepository.musicEnabled

    fun setMusicEnabled(enabled: Boolean) = settingsRepository.setMusicEnabled(enabled)

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

    private companion object {
        const val ACCOUNT_NUDGE_GAMES_THRESHOLD = 3
    }
}
