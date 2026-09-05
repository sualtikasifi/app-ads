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
    private val accountDeletionRepository: AccountDeletionRepository
) : ViewModel() {

    private val _actionState = MutableStateFlow(AccountUiState(isGoogleSignInConfigured = authRepository.isGoogleSignInConfigured))

    val uiState: StateFlow<AccountUiState> = combine(
        authRepository.authState,
        backupRepository.lastBackupAtMillis,
        _actionState
    ) { authState, lastBackupAtMillis, action ->
        action.copy(authState = authState, lastBackupAtMillis = lastBackupAtMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _actionState.value
    )

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
                    // The uid just changed to that older account's — its backup, if any, is what this device should have.
                    restoreBackup()
                }
                .onFailure { error -> _actionState.value = handleLinkFailure(error) }
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

    /** Opens the picker for a different Google account and moves this uid's link to it. */
    fun switchGoogleAccount() {
        _actionState.value = _actionState.value.copy(showSwitchAccountPrompt = false, isLinking = true, errorMessage = null, message = null)
        viewModelScope.launch {
            authRepository.switchGoogleAccount()
                .onSuccess { _actionState.value = _actionState.value.copy(isLinking = false, message = UiText.of(R.string.account_linked_success)) }
                .onFailure { error -> _actionState.value = handleLinkFailure(error) }
        }
    }
}
