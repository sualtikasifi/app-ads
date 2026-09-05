package com.sualtikasifi.cizimhafiza.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** What this device's Firebase Auth session currently is — see [AuthRepository.authState]. */
sealed interface AuthState {
    /** No session yet, or it hasn't loaded. Callers needing a uid should use [AuthRepository.ensureSignedIn]. */
    data object Unknown : AuthState

    /** Signed in anonymously — a real, stable uid, but tied to this install only. */
    data object Anonymous : AuthState

    /** Linked to a permanent Google account — survives an uninstall/reinstall or a new device. */
    data class Linked(val email: String?, val displayName: String?, val photoUrl: String?) : AuthState
}

/**
 * How [AuthRepository.signInWithGoogle] resolved — which decides whether
 * the caller must go and adopt the account's cloud backup.
 */
sealed interface SignInOutcome {
    /**
     * The Google account had no Firebase identity of its own, so it was
     * linked onto the uid this device was already using. Nothing about the
     * player changed: whatever progress they made before signing in is
     * still theirs, and now has an account to be backed up to.
     */
    data object LinkedToDevice : SignInOutcome

    /**
     * The Google account already owned a Firebase identity (another device,
     * or this one before a sign-out), and this session is now signed in as
     * it. The uid CHANGED, so local progress belongs to somebody else until
     * the caller runs BackupRepository.switchToAccount.
     */
    data object SwitchedToAccount : SignInOutcome
}

/** Why [AuthRepository.signInWithGoogle] didn't end in [AuthState.Linked]. */
sealed interface LinkFailure {
    /** The user closed the account picker without choosing one. Not an error worth surfacing. */
    data object Cancelled : LinkFailure

    /**
     * The device has no Google account to offer, so the picker had nothing
     * to show. Distinct from [Other] because the fix is the player's to
     * make — add an account in system settings — and a generic "linking
     * failed" message gives them nothing to act on.
     */
    data object NoGoogleAccount : LinkFailure

    data class Other(val message: String?) : LinkFailure
}

/** Carries a typed [LinkFailure] through a [Result.failure] without losing it to a generic message string. */
class LinkFailureException(val failure: LinkFailure) : Exception()

/**
 * Wraps Firebase Auth's anonymous session and its optional upgrade to a
 * permanent Google-linked one (see AuthRepositoryImpl for why linking,
 * rather than a fresh sign-in, is what preserves this device's existing
 * uid/friends/rooms). Every other repository's own `signInAnonymously()`
 * fallback (FriendRepositoryImpl.requireUid, etc.) keeps working exactly as
 * before — this only adds the optional upgrade path, it does not replace
 * anonymous auth as the default.
 */
interface AuthRepository {

    val authState: StateFlow<AuthState>

    /** True once google-services.json has an OAuth web client configured — see BASELINE for AdMob/UMP's
     * identical "needs one manual console step" pattern. False means the linking UI should explain
     * that setup step instead of showing a button that can only fail. */
    val isGoogleSignInConfigured: Boolean

    /** Returns the current uid, signing in anonymously first if there is no session yet. */
    suspend fun ensureSignedIn(): String

    /**
     * Opens the system account picker and signs in as the chosen Google
     * account — the ONE way into an account, whether or not this app has
     * seen it before. [SignInOutcome] says which of the two things
     * happened, because the caller's next step differs:
     *
     *  - a Google account with no Firebase identity yet is LINKED onto this
     *    device's current anonymous uid, carrying whatever progress was
     *    made before signing in into the new account;
     *  - one that already has an identity is SIGNED INTO, which changes the
     *    uid, and the caller must then run
     *    [BackupRepository.switchToAccount] so what is on screen belongs to
     *    the account that is now signed in.
     *
     * There is deliberately no "switch account" call beside this one. A
     * player changing accounts signs out and back in, which routes through
     * [signOut]'s backup-then-wipe and lands here with a clean device —
     * the only ordering in which the wrong profile cannot survive.
     */
    suspend fun signInWithGoogle(): Result<SignInOutcome>

    /**
     * Ends the Google session and leaves this device on a FRESH anonymous
     * uid, ready for the next player.
     *
     * Signing out is the only genuinely destructive thing here: everything
     * on the phone belongs to the account being left, so the caller must
     * have put it in the cloud first. The strict order is
     * BackupRepository.backupNow → [signOut] → BackupRepository.clearLocalProgress,
     * and it is not arbitrary — the wipe has to come AFTER the session ends
     * so no auto-backup can fire in between and overwrite the account's
     * good cloud copy with the freshly-emptied local one.
     */
    suspend fun signOut(): Result<Unit>
}
