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

/** Why [AuthRepository.linkWithGoogle] didn't end in [AuthState.Linked] on THIS uid. */
sealed interface LinkFailure {
    /** The Google account is already linked to a *different* Firebase user (e.g. this is a reinstall
     * on a device that previously linked the same Google account). [AuthRepository.switchToExistingAccount]
     * signs into that account instead — the caller decides whether to offer that as a next step. */
    data object CredentialAlreadyInUse : LinkFailure

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
     * Opens the system account picker and links the chosen Google account to
     * THIS device's current (anonymous) Firebase user, preserving its uid.
     */
    suspend fun linkWithGoogle(): Result<Unit>

    /**
     * Signs into the pre-existing Firebase account the last [linkWithGoogle]
     * attempt found ([LinkFailure.CredentialAlreadyInUse]) instead of this
     * device's anonymous one. This uid is DIFFERENT from before — the
     * caller is expected to follow up with [com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository.restoreLatest].
     */
    suspend fun switchToExistingAccount(): Result<Unit>

    /**
     * Removes the Google provider from this device's current user, reverting
     * it to anonymous. The uid itself never changes — unlinking and linking
     * both operate on the SAME [com.google.firebase.auth.FirebaseUser] — so
     * any existing cloud backup under this uid is untouched and reachable
     * again the moment this device (re-)links any Google account.
     */
    suspend fun unlinkGoogle(): Result<Unit>

    /**
     * Replaces the Google account currently linked with a different one the
     * player picks from the account chooser. The new account is obtained
     * BEFORE the old one is unlinked, so cancelling the picker leaves this
     * device exactly as linked as it was — the one moment a partial failure
     * here would actually cost something.
     *
     * The chosen account may already have its OWN uid elsewhere (it was
     * linked to this app on another device, or before this device's most
     * recent unlink) — in that case this signs into that EXISTING identity
     * instead of relinking onto this device's current one. The returned
     * [Boolean] tells the caller which happened: true means the uid changed
     * to that different, pre-existing identity (BackupRepository.switchToAccount
     * must run so local progress matches it) — false means the same uid
     * kept its local progress and merely has a different Google account
     * linked to it now, exactly as before.
     */
    suspend fun switchGoogleAccount(): Result<Boolean>
}
