package com.sualtikasifi.cizimhafiza.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.repository.AccountDeletionRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ReauthenticationRequiredException
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailure
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailureException
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import com.sualtikasifi.cizimhafiza.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val authState: AuthState = AuthState.Unknown,
    val isGoogleSignInConfigured: Boolean = false,
    val lastBackupAtMillis: Long? = null,
    val nickname: String = "",
    val isLinking: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val message: UiText? = null,
    val errorMessage: UiText? = null,
    /** The Google account chosen is already linked to a DIFFERENT Firebase user — offer to switch to it instead. */
    val showAlreadyLinkedPrompt: Boolean = false,
    val showDeletePrompt: Boolean = false,
    val isDeleting: Boolean = false,
    /** Set once the account is gone, so the screen can send the player back to the menu. */
    val accountDeleted: Boolean = false,
    val showUnlinkPrompt: Boolean = false,
    val showSwitchAccountPrompt: Boolean = false
) {
    val isLinked: Boolean get() = authState is AuthState.Linked
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    private val accountDeletionRepository: AccountDeletionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _actionState = MutableStateFlow(AccountUiState(isGoogleSignInConfigured = authRepository.isGoogleSignInConfigured))

    val uiState: StateFlow<AccountUiState> = combine(
        authRepository.authState,
        backupRepository.lastBackupAtMillis,
        settingsRepository.nickname,
        _actionState
    ) { authState, lastBackupAtMillis, nickname, action ->
        action.copy(authState = authState, lastBackupAtMillis = lastBackupAtMillis, nickname = nickname)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _actionState.value
    )

    init {
        // A player who links Google having never typed a nickname is far
        // more common than one switching FROM a chosen nickname TO Google's
        // — so this only ever fills a blank, never overwrites a name the
        // player actually picked. Watches BOTH flows, not just authState:
        // a fresh account switch (syncAfterAccountSwitch) clears the
        // nickname to blank well after authState already turned Linked, so
        // reacting to authState alone would miss it — this also catches
        // the moment the nickname itself goes blank while already linked.
        viewModelScope.launch {
            combine(authRepository.authState, settingsRepository.nickname) { authState, nickname -> authState to nickname }
                .collect { (authState, nickname) ->
                    val linked = authState as? AuthState.Linked ?: return@collect
                    val displayName = linked.displayName?.trim()
                    if (nickname.isBlank() && !displayName.isNullOrBlank()) {
                        settingsRepository.setNickname(displayName)
                    }
                }
        }
    }

    fun setNickname(name: String) {
        settingsRepository.setNickname(name)
    }

    fun promptDeleteAccount() { _actionState.value = _actionState.value.copy(showDeletePrompt = true) }

    fun dismissDeletePrompt() { _actionState.value = _actionState.value.copy(showDeletePrompt = false) }

    /**
     * Deletes the account and everything attached to it — see
     * AccountDeletionRepository for what "everything" covers and why Google
     * Play requires this to exist at all.
     */
    fun deleteAccount() {
        if (_actionState.value.isDeleting) return
        _actionState.value = _actionState.value.copy(isDeleting = true, showDeletePrompt = false, errorMessage = null)
        viewModelScope.launch {
            accountDeletionRepository.deleteAccountAndData()
                .onSuccess {
                    _actionState.value = _actionState.value.copy(isDeleting = false, accountDeleted = true)
                }
                .onFailure { error ->
                    _actionState.value = _actionState.value.copy(
                        isDeleting = false,
                        errorMessage = UiText.of(
                            if (error is ReauthenticationRequiredException) {
                                R.string.account_delete_reauth_needed
                            } else {
                                R.string.account_delete_failed
                            }
                        )
                    )
                }
        }
    }

    fun linkGoogleAccount() {
        if (_actionState.value.isLinking) return
        _actionState.value = _actionState.value.copy(isLinking = true, errorMessage = null, message = null)
        viewModelScope.launch {
            authRepository.linkWithGoogle()
                .onSuccess {
                    _actionState.value = _actionState.value.copy(isLinking = false, message = UiText.of(R.string.account_linked_success))
                }
                .onFailure { error -> _actionState.value = handleLinkFailure(error) }
        }
    }

    /** Called after [AccountUiState.showAlreadyLinkedPrompt]: signs into the pre-existing account instead. */
    fun switchToExistingAccount() {
        _actionState.value = _actionState.value.copy(isLinking = true, showAlreadyLinkedPrompt = false, errorMessage = null)
        viewModelScope.launch {
            authRepository.switchToExistingAccount()
                .onSuccess {
                    _actionState.value = _actionState.value.copy(isLinking = false)
                    // The uid just changed to that older account's — this
                    // device's local level/XP/nickname belonged to the
                    // PREVIOUS uid and must be replaced outright, not merged
                    // (see BackupRepository.switchToAccount's doc).
                    syncAfterAccountSwitch()
                }
                .onFailure { error -> _actionState.value = handleLinkFailure(error) }
        }
    }

    /**
     * Replaces local progress with whatever the newly-active uid's own
     * backup holds (or resets to a fresh profile if it has none) — the
     * account-switch counterpart to [restoreBackup], which merges instead
     * because it assumes the SAME player. Never call this after a link/
     * unlink that keeps the same uid; only after a uid actually changed.
     */
    private fun syncAfterAccountSwitch() {
        _actionState.value = _actionState.value.copy(isLinking = false, isRestoring = true, errorMessage = null, message = null)
        viewModelScope.launch {
            backupRepository.switchToAccount()
                .onSuccess { hadBackup ->
                    val text = if (hadBackup) R.string.account_switched_success else R.string.account_switched_fresh
                    _actionState.value = _actionState.value.copy(isRestoring = false, message = UiText.of(text))
                }
                .onFailure { _actionState.value = _actionState.value.copy(isRestoring = false, errorMessage = UiText.of(R.string.account_restore_failed)) }
        }
    }

    fun dismissAlreadyLinkedPrompt() {
        _actionState.value = _actionState.value.copy(showAlreadyLinkedPrompt = false)
    }

    private fun handleLinkFailure(error: Throwable): AccountUiState {
        val current = _actionState.value.copy(isLinking = false)
        return when ((error as? LinkFailureException)?.failure) {
            LinkFailure.Cancelled -> current
            LinkFailure.CredentialAlreadyInUse -> current.copy(showAlreadyLinkedPrompt = true)
            LinkFailure.NoGoogleAccount -> current.copy(errorMessage = UiText.of(R.string.account_no_google_account))
            else -> current.copy(errorMessage = UiText.of(R.string.account_link_failed))
        }
    }

    fun backupNow() {
        if (_actionState.value.isBackingUp) return
        _actionState.value = _actionState.value.copy(isBackingUp = true, errorMessage = null, message = null)
        viewModelScope.launch {
            backupRepository.backupNow()
                .onSuccess { _actionState.value = _actionState.value.copy(isBackingUp = false, message = UiText.of(R.string.account_backup_success)) }
                .onFailure { _actionState.value = _actionState.value.copy(isBackingUp = false, errorMessage = UiText.of(R.string.account_backup_failed)) }
        }
    }

    fun restoreBackup() {
        if (_actionState.value.isRestoring) return
        _actionState.value = _actionState.value.copy(isRestoring = true, errorMessage = null, message = null)
        viewModelScope.launch {
            backupRepository.restoreLatest()
                .onSuccess { restored ->
                    val text = if (restored) R.string.account_restore_success else R.string.account_restore_nothing
                    _actionState.value = _actionState.value.copy(isRestoring = false, message = UiText.of(text))
                }
                .onFailure { _actionState.value = _actionState.value.copy(isRestoring = false, errorMessage = UiText.of(R.string.account_restore_failed)) }
        }
    }

    fun dismissMessages() {
        _actionState.value = _actionState.value.copy(message = null, errorMessage = null)
    }

    fun promptUnlink() { _actionState.value = _actionState.value.copy(showUnlinkPrompt = true) }

    fun dismissUnlinkPrompt() { _actionState.value = _actionState.value.copy(showUnlinkPrompt = false) }

    /** Reverts this device to anonymous. The local backup under this uid is untouched — see AuthRepository.unlinkGoogle. */
    fun unlinkGoogleAccount() {
        _actionState.value = _actionState.value.copy(showUnlinkPrompt = false, isLinking = true, errorMessage = null, message = null)
        viewModelScope.launch {
            authRepository.unlinkGoogle()
                .onSuccess { _actionState.value = _actionState.value.copy(isLinking = false, message = UiText.of(R.string.account_unlinked_success)) }
                .onFailure { _actionState.value = _actionState.value.copy(isLinking = false, errorMessage = UiText.of(R.string.account_unlink_failed)) }
        }
    }

    fun promptSwitchAccount() { _actionState.value = _actionState.value.copy(showSwitchAccountPrompt = true) }

    fun dismissSwitchAccountPrompt() { _actionState.value = _actionState.value.copy(showSwitchAccountPrompt = false) }

    /**
     * Opens the picker for a different Google account and moves this uid's
     * link to it — unless the chosen account already has its OWN uid
     * elsewhere, in which case AuthRepository quietly signs into that
     * existing identity instead (see its doc). Only THAT case actually
     * changes which player this device is — syncAfterAccountSwitch() must
     * run then, and only then: the ordinary same-uid relink already has
     * the right local progress, and hard-replacing it anyway would wipe a
     * real player's level stars and achievements for nothing.
     */
    fun switchGoogleAccount() {
        _actionState.value = _actionState.value.copy(showSwitchAccountPrompt = false, isLinking = true, errorMessage = null, message = null)
        viewModelScope.launch {
            authRepository.switchGoogleAccount()
                .onSuccess { identityChanged ->
                    if (identityChanged) {
                        syncAfterAccountSwitch()
                    } else {
                        _actionState.value = _actionState.value.copy(isLinking = false, message = UiText.of(R.string.account_linked_success))
                    }
                }
                .onFailure { error -> _actionState.value = handleLinkFailure(error) }
        }
    }
}
