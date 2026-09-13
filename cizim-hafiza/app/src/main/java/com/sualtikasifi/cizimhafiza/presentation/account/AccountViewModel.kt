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
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val authState: AuthState = AuthState.Unknown,
    val isGoogleSignInConfigured: Boolean = false,
    val lastBackupAtMillis: Long? = null,
    val nickname: String = "",
    /**
     * What the player has TYPED, or null while they have not touched the
     * field at all.
     *
     * Null is the whole point. The draft used to be a plain String starting
     * at "", which is indistinguishable from "the player cleared it" — so
     * the field had to be refilled from the stored name by a collector, and
     * any moment that collector did not fire (a fresh ViewModel, a save, a
     * name changed from elsewhere) left the box empty while a perfectly good
     * name was stored. Untouched now MEANS the stored name, by construction,
     * so there is nothing left to keep in sync.
     */
    val nicknameEdit: String? = null,
    val nicknameSaveState: NicknameSaveState = NicknameSaveState.Idle,
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
     * Only account DELETION sets this, and only deletion should.
     *
     * Signing in and out no longer restart the app. They never needed the
     * whole process for its own sake — they needed the in-memory copies of
     * the outgoing account to go, and those are now either StateFlows the
     * repositories update as they write (level, XP, name, frame, streak) or
     * caches keyed by the uid they belong to (see
     * FriendRepositoryImpl.cachedFriendCode), so nothing survives the switch
     * that could describe the wrong player.
     *
     * Deletion is genuinely different: it deletes the preference FILES
     * outright, behind the back of every StateFlow reading from them, and
     * takes the device-scoped settings with them. There is no in-memory
     * state left worth reconciling, and a restart after "delete my account"
     * is what a player expects anyway.
     */
    val restartRequired: Boolean = false
) {
    val isSignedIn: Boolean get() = authState is AuthState.Linked

    /** What the text field shows: the edit if there is one, else the saved name. */
    val nicknameDraft: String get() = nicknameEdit ?: nickname

    /**
     * Whether there is anything to save. A blank name is never savable —
     * it is what other players see, and the app has no second name to fall
     * back on once one has been chosen.
     */
    val canSaveNickname: Boolean
        get() = nicknameSaveState == NicknameSaveState.Idle &&
            nicknameDraft.isNotBlank() &&
            nicknameDraft.trim() != nickname
}

/** Drives the Kaydet button: idle → saving → the confirmation, then back. */
enum class NicknameSaveState { Idle, Saving, Saved }

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
    private val friendRepository: FriendRepository,
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

    /**
     * Nothing is written until Kaydet.
     *
     * The nickname used to be stored on every keystroke, so clearing the
     * field wrote a BLANK name — and util.ProfileNameSynchronizer exists
     * precisely to fill a blank name from the Google account, which it did,
     * instantly, while the player was still deleting. Holding the edit here
     * and publishing it only on save means there is no blank for anything to
     * react to, and an abandoned edit costs nothing.
     */
    fun setNicknameDraft(name: String) {
        _actionState.value = _actionState.value.copy(
            nicknameEdit = name,
            // Typing again retracts the confirmation — it described the
            // previous save, not this text.
            nicknameSaveState = NicknameSaveState.Idle
        )
    }

    /**
     * Saves the name everywhere it is visible, not just on this device.
     *
     * Three places, because a nickname is read from three: this device's own
     * settings (every screen in the app), the public profile document (a
     * friend's list and the league table), and the Firebase account profile
     * (the header on this screen, which was still showing the Google name).
     * Only the first is required — the other two are network writes, and a
     * rename that is correct locally but could not be published is worth
     * confirming rather than refusing.
     */
    fun saveNickname() {
        val state = _actionState.value
        if (!state.canSaveNickname) return
        val name = state.nicknameDraft.trim()
        _actionState.value = state.copy(nicknameSaveState = NicknameSaveState.Saving)
        viewModelScope.launch {
            settingsRepository.setNickname(name)
            runCatching { friendRepository.updatePublicNickname(name) }
            authRepository.updateDisplayName(name)
            _actionState.value = _actionState.value.copy(
                // Released rather than set to `name`: the edit is finished,
                // so the field goes back to mirroring what is stored — which
                // is this name, and stays right if anything renames the
                // player afterwards.
                nicknameEdit = null,
                nicknameSaveState = NicknameSaveState.Saved
            )
            delay(SAVED_BADGE_MS)
            if (_actionState.value.nicknameSaveState == NicknameSaveState.Saved) {
                _actionState.value = _actionState.value.copy(nicknameSaveState = NicknameSaveState.Idle)
            }
        }
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
                                nameFromEmailIfUnnamed()
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

    /**
     * Gives an unnamed player the local part of the address they signed in
     * with — raunen3075@gmail.com becomes raunen3075.
     *
     * Only when nothing is stored, and only AFTER any restore has run, so
     * the name an account already carries always wins over the address it
     * happens to use. Anyone who dislikes it changes it on this same screen.
     */
    private fun nameFromEmailIfUnnamed() {
        if (settingsRepository.nickname.value.isNotBlank()) return
        val email = (authRepository.authState.value as? AuthState.Linked)?.email ?: return
        val local = email.substringBefore('@').trim()
        if (local.isNotEmpty()) settingsRepository.setNickname(local)
    }

    private suspend fun adoptSignedInAccount() {
        backupRepository.switchToAccount()
            .onSuccess {
                // After the restore, never before: a returning account brings
                // its own name, and only an account that has none falls back
                // to its address.
                nameFromEmailIfUnnamed()
                // No restart. The level, frame, nickname and streak this
                // screen and the main menu show are all StateFlows that
                // switchToAccount has just written through, so they are
                // already the new account's by the time this line runs.
                _actionState.value = _actionState.value.copy(
                    isBusy = false,
                    message = UiText.of(R.string.account_signed_in_restored)
                )
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
        val failure = (error as? LinkFailureException)?.failure
        val detail = (failure as? LinkFailure.Other)?.message
        return when (failure) {
            LinkFailure.Cancelled -> current
            LinkFailure.NoGoogleAccount -> current.copy(errorMessage = UiText.of(R.string.account_no_google_account))
            // The detail is shown rather than swallowed. It is a Play Services
            // status code, which is not pretty, but a player who can read
            // "10" back to us turns an unreproducible report into a
            // one-line diagnosis — see AuthRepositoryImpl.
            is LinkFailure.Other -> current.copy(
                errorMessage = detail
                    ?.takeIf { it.isNotBlank() }
                    ?.let { UiText.of(R.string.account_sign_in_failed_code, it) }
                    ?: UiText.of(R.string.account_sign_in_failed)
            )
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
                                    message = if (uploaded) UiText.of(R.string.account_signed_out_done) else null,
                                    // The field follows the stored name,
                                    // which the wipe just blanked — dropping
                                    // any half-typed edit stops it showing
                                    // the departed account's name.
                                    nicknameEdit = null
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

    private companion object {
        /** Long enough to read the confirmation, short enough not to look stuck. */
        const val SAVED_BADGE_MS = 2_200L
    }
}
