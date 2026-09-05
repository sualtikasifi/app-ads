package com.sualtikasifi.cizimhafiza.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.repository.AccountDeletionRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ReauthenticationRequiredException
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailure
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailureException
import com.sualtikasifi.cizimhafiza.domain.repository.SignInOutcome
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import com.sualtikasifi.cizimhafiza.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val level: Int = 1,
    val frame: AvatarFrame = AvatarFrame.DEFAULT,
    /** A sign-in or sign-out is running; the whole account section is frozen behind a spinner. */
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val errorMessage: UiText? = null,
    val showSignOutPrompt: Boolean = false,
    val showDeletePrompt: Boolean = false,
    val isDeleting: Boolean = false,
    /**
     * The account actually changed and every in-memory copy of the previous
     * one has to go with it — the screen reads this and restarts the app
     * (see util.AppRestarter for why nothing short of that is reliable).
     */
    val restartRequired: Boolean = false
) {
    val isSignedIn: Boolean get() = authState is AuthState.Linked
}

/**
 * The Hesap screen's state, and the two operations that can change which
 * player this device belongs to.
 *
 * Deliberately only two: sign in, sign out. The older screen also offered
 * "Şimdi Yedekle", "Yedeği Geri Yükle" and "Hesap Değiştir" — three
 * buttons that between them let a player put the device into states the
 * app could not describe (linked to account B while holding account A's
 * level, a backup restored on top of a different account's progress). All
 * three are gone: backups run themselves (see util.AutoBackupPublisher),
 * and changing accounts is signing out and back in, which is the one path
 * that provably cannot mix two players' data.
 */
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
        settingsRepository.lifetimeXp,
        _actionState
    ) { authState, lastBackupAtMillis, nickname, lifetimeXp, action ->
        val level = PlayerLevel.levelForXp(lifetimeXp)
        action.copy(
            authState = authState,
            lastBackupAtMillis = lastBackupAtMillis,
            nickname = nickname,
            level = level,
            frame = AvatarFrame.resolve(settingsRepository.selectedAvatarFrameId.value, level)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _actionState.value
    )

    fun setNickname(name: String) {
        settingsRepository.setNickname(name)
    }

    /**
     * Signs in, then makes sure what is on screen belongs to the account
     * that just signed in.
     *
     * [SignInOutcome.LinkedToDevice] needs no adoption — that account had
     * no progress of its own, and the guest progress it just inherited is
     * already correct locally and gets backed up on the next tick.
     * [SignInOutcome.SwitchedToAccount] changed the uid, so local progress
     * is somebody else's until switchToAccount() replaces it.
     */
    fun signIn() {
        if (_actionState.value.isBusy) return
        _actionState.value = _actionState.value.copy(isBusy = true, errorMessage = null, message = null)
        viewModelScope.launch {
            // Held across the sign-in itself, not just the local swap that
            // follows it: the moment signInWithCredential returns, this
            // device is the NEW account while still holding the OLD one's
            // progress, and an onPause in that window would upload the
            // wrong player into the new account's backup — see
            // BackupRepository.beginAccountTransition.
            backupRepository.beginAccountTransition()
            try {
                authRepository.signInWithGoogle()
                    .onSuccess { outcome ->
                        when (outcome) {
                            SignInOutcome.LinkedToDevice -> {
                                _actionState.value = _actionState.value.copy(
                                    isBusy = false,
                                    message = UiText.of(R.string.account_signed_in_progress_kept)
                                )
                            }
                            SignInOutcome.SwitchedToAccount -> adoptSignedInAccount()
                        }
                    }
                    .onFailure { error -> _actionState.value = handleSignInFailure(error) }
            } finally {
                backupRepository.endAccountTransition()
            }
        }
    }

    private suspend fun adoptSignedInAccount() {
        backupRepository.switchToAccount()
            .onSuccess {
                // Restart rather than report: the level, frame, friend code
                // and league row just changed underneath every screen in the
                // app, and only a fresh process is guaranteed to be showing
                // the new account everywhere.
                _actionState.value = _actionState.value.copy(isBusy = false, restartRequired = true)
            }
            .onFailure {
                _actionState.value = _actionState.value.copy(
                    isBusy = false,
                    errorMessage = UiText.of(R.string.account_sign_in_failed)
                )
            }
    }

    private fun handleSignInFailure(error: Throwable): AccountUiState {
        val current = _actionState.value.copy(isBusy = false)
        return when ((error as? LinkFailureException)?.failure) {
            LinkFailure.Cancelled -> current
            LinkFailure.NoGoogleAccount -> current.copy(errorMessage = UiText.of(R.string.account_no_google_account))
            else -> current.copy(errorMessage = UiText.of(R.string.account_sign_in_failed))
        }
    }

    fun promptSignOut() { _actionState.value = _actionState.value.copy(showSignOutPrompt = true) }

    fun dismissSignOutPrompt() { _actionState.value = _actionState.value.copy(showSignOutPrompt = false) }

    /**
     * Archive on this device → cloud backup → sign out → wipe, in that
     * exact order, and only the FIRST of those may not fail.
     *
     * The order encodes what is actually recoverable. Everything the wipe
     * removes has to exist somewhere else first, and of the two places it
     * can exist only one is under this app's control: the phone. The
     * archive is therefore the hard precondition — if it cannot be written
     * and read back, nothing is destroyed and the player keeps their
     * account exactly as it was.
     *
     * The cloud upload is attempted next but deliberately does NOT block
     * the sign-out. Requiring it meant a player with no connection could
     * not sign out at all, and — far worse — a backup that quietly did
     * nothing still reported success, which is a signed permission to
     * delete unsaved progress. Now a failed upload costs only a warning:
     * the progress is on the phone either way, and the next successful
     * backup carries it up.
     */
    fun signOut() {
        if (_actionState.value.isBusy) return
        _actionState.value = _actionState.value.copy(showSignOutPrompt = false, isBusy = true, errorMessage = null, message = null)
        viewModelScope.launch {
            val archived = backupRepository.archiveForSignOut().isSuccess
            if (!archived) {
                _actionState.value = _actionState.value.copy(
                    isBusy = false,
                    errorMessage = UiText.of(R.string.account_sign_out_backup_failed)
                )
                return@launch
            }
            val uploaded = backupRepository.backupNow().isSuccess
            // Opened only AFTER the backup above, which must genuinely run —
            // and closed only once the device is wiped, so nothing can
            // upload the emptied device over the account that just left.
            backupRepository.beginAccountTransition()
            try {
                authRepository.signOut()
                    .onSuccess {
                        // Checked, never assumed. A wipe that threw halfway
                        // used to be swallowed here and still restart the
                        // app "successfully" — straight back into the
                        // profile the player had just signed out of, with
                        // nothing on screen admitting anything had gone
                        // wrong. If the device still holds the old progress,
                        // say so instead of pretending.
                        backupRepository.clearLocalProgress()
                            .onSuccess {
                                _actionState.value = _actionState.value.copy(
                                    isBusy = false,
                                    // Told, not hidden: the account is safe on
                                    // this phone but the cloud copy is behind,
                                    // which matters if they sign in elsewhere.
                                    errorMessage = if (uploaded) null else UiText.of(R.string.account_sign_out_local_only),
                                    restartRequired = true
                                )
                            }
                            .onFailure {
                                _actionState.value = _actionState.value.copy(
                                    isBusy = false,
                                    errorMessage = UiText.of(R.string.account_sign_out_wipe_failed)
                                )
                            }
                    }
                    .onFailure {
                        _actionState.value = _actionState.value.copy(
                            isBusy = false,
                            errorMessage = UiText.of(R.string.account_sign_out_failed)
                        )
                    }
            } finally {
                backupRepository.endAccountTransition()
            }
        }
    }

    fun promptDeleteAccount() { _actionState.value = _actionState.value.copy(showDeletePrompt = true) }

    fun dismissDeletePrompt() { _actionState.value = _actionState.value.copy(showDeletePrompt = false) }

    /**
     * Deletes the account and everything attached to it — see
     * AccountDeletionRepository for what "everything" covers and why Google
     * Play requires this to exist at all.
     *
     * Restarts on success for the same reason an account switch does, and
     * one more besides: deletion clears the preference FILES directly,
     * behind the back of the StateFlows holding those values, so without a
     * restart the menu would keep showing the deleted player's level and
     * name until the process happened to die.
     */
    fun deleteAccount() {
        if (_actionState.value.isDeleting) return
        _actionState.value = _actionState.value.copy(isDeleting = true, showDeletePrompt = false, errorMessage = null)
        viewModelScope.launch {
            accountDeletionRepository.deleteAccountAndData()
                .onSuccess {
                    _actionState.value = _actionState.value.copy(isDeleting = false, restartRequired = true)
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

    fun dismissMessages() {
        _actionState.value = _actionState.value.copy(message = null, errorMessage = null)
    }
}
