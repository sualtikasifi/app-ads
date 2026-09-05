package com.sualtikasifi.cizimhafiza.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailure
import com.sualtikasifi.cizimhafiza.domain.repository.LinkFailureException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * See AuthRepository's doc for why this only ADDS an upgrade path onto the
 * existing anonymous-auth default rather than replacing it.
 *
 * Uses the classic `GoogleSignInClient` API, not the newer Credential
 * Manager "Sign in with Google". Credential Manager was tried first and
 * pulled back out: on MIUI/HyperOS (Xiaomi/Redmi/POCO — a large share of
 * the Turkish install base) it routes account selection through a Play
 * Services "reauth" step that fails outright, confirmed on-device via
 * Logcat — `GetCredentialCancellationException: [16] Account reauth
 * failed` — every single time, on every account, with no error surfaced
 * to the player: the picker just closes as if nothing happened. This API
 * has no reauth step to fail; it is what every Firebase Android app used
 * for years before Credential Manager existed, and MIUI's own account
 * chooser (`GoogleSignInClient.signInIntent`) is unaffected by this bug.
 *
 * [linkWithGoogle] deliberately calls `linkWithCredential`, not
 * `signInWithCredential`: linking keeps this device's existing Firebase
 * uid, which is what every friend, room and drawing already saved under
 * that uid depends on. A plain sign-in would hand back a DIFFERENT
 * (whichever the Google account already had, or a fresh one) uid, silently
 * orphaning everything tied to the anonymous one — exactly the loss C11
 * exists to prevent, not cause.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val googleSignInLauncher: GoogleSignInLauncher,
    private val friendRepository: FriendRepository
) : AuthRepository {

    // google-services.json only contains an OAuth web client once Google
    // Sign-In has been enabled for this Firebase project in the console —
    // until then this resource genuinely does not exist, so it's looked up
    // by name (rather than a compile-time R.string reference, which would
    // fail to compile the moment the project is re-synced from a
    // google-services.json still missing it) and the UI is expected to
    // check isGoogleSignInConfigured before offering the button at all.
    private val webClientId: String? by lazy {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        resId.takeIf { it != 0 }?.let { context.getString(it) }
    }

    override val isGoogleSignInConfigured: Boolean get() = webClientId != null

    private val _authState = MutableStateFlow<AuthState>(deriveState(auth.currentUser))
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        auth.addAuthStateListener { _authState.value = deriveState(it.currentUser) }
    }

    /**
     * Deliberately reads the Google entry in [FirebaseUser.getProviderData]
     * rather than the top-level [FirebaseUser.getEmail]/`getDisplayName`/
     * `getPhotoUrl`. Those top-level fields are sticky: Firebase Auth only
     * fills them in when they are still blank, it never overwrites them —
     * so unlinking Google and linking a DIFFERENT Google account left the
     * screen showing the first account's email forever, because the
     * top-level fields were set once from the first link and never touched
     * again. The provider-data entry, by contrast, IS replaced by every
     * link/unlink, so it is the only field that is ever actually current.
     *
     * [FirebaseUser.isAnonymous] is avoided for the same reason: it is
     * derived from this same provider list on the SDK's local copy of the
     * user, and that copy can lag a beat behind an unlink — reading the
     * list directly here needs nothing to have "flipped" first.
     */
    private fun deriveState(user: FirebaseUser?): AuthState {
        if (user == null) return AuthState.Unknown
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            ?: return AuthState.Anonymous
        return AuthState.Linked(email = google.email, displayName = google.displayName, photoUrl = google.photoUrl?.toString())
    }

    /**
     * Pushes a fresh read of [FirebaseAuth.getCurrentUser] into [_authState]
     * by hand. [FirebaseAuth.AuthStateListener] fires on sign-in/sign-out —
     * a UID change — not on linking or unlinking a provider on the SAME
     * already-signed-in user, so linkWithGoogle/unlinkGoogle would otherwise
     * leave the UI showing the pre-link state until the next app launch
     * re-read auth.currentUser from scratch.
     *
     * Reloads first: link/unlink already update the SDK's local user
     * synchronously in the common case, but a reload costs one cheap call
     * and removes any doubt that this is reading anything but the server's
     * current answer, right after the exact operations most likely to have
     * just changed it.
     */
    private suspend fun refreshAuthState() {
        runCatching { auth.currentUser?.reload()?.await() }
        _authState.value = deriveState(auth.currentUser)
    }

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun linkWithGoogle(): Result<Unit> {
        val firebaseCredential = googleCredential().getOrElse { return Result.failure(it) }
        return runCatching {
            ensureSignedIn()
            auth.currentUser!!.linkWithCredential(firebaseCredential).await()
            Unit
        }.onSuccess {
            // See refreshAuthState's doc — linking does not fire
            // AuthStateListener on its own, so the UI would otherwise sit on
            // "Bağlı değil" until the app was relaunched.
            refreshAuthState()
        }.recoverCatching { error ->
            // Not surfaced to the player as-is (that's what LinkFailure is
            // for) — logged so a failure that reaches neither the "already
            // linked" dialog nor a mapped error message is still visible
            // somewhere, instead of vanishing into a Result the UI quietly
            // resets from.
            Log.w(TAG, "linkWithCredential failed", error)
            if (error is FirebaseAuthUserCollisionException) throw LinkFailureException(LinkFailure.CredentialAlreadyInUse)
            throw error
        }
    }

    override suspend fun switchToExistingAccount(): Result<Unit> =
        googleCredential().mapCatching { firebaseCredential ->
            // Must run BEFORE signInWithCredential: it needs to still be
            // signed in as this (about-to-be-abandoned) uid to detach itself
            // from its own friends — see FriendRepository.prepareFriendMigration.
            val migratedFriends = runCatching { friendRepository.prepareFriendMigration() }
                .onFailure { Log.w(TAG, "prepareFriendMigration failed", it) }
                .getOrDefault(emptyList())
            auth.signInWithCredential(firebaseCredential).await()
            // Best-effort, and must never undo or block the account switch
            // that already happened above — see adoptMigratedFriends's doc.
            runCatching { friendRepository.adoptMigratedFriends(migratedFriends) }
                .onFailure { Log.w(TAG, "adoptMigratedFriends failed", it) }
            Unit
        }.onSuccess { refreshAuthState() }

    override suspend fun unlinkGoogle(): Result<Unit> = runCatching {
        auth.currentUser?.unlink(GoogleAuthProvider.PROVIDER_ID)?.await()
        Unit
    }.onSuccess {
        refreshAuthState()
    }.onFailure {
        Log.w(TAG, "unlinkGoogle failed", it)
    }

    override suspend fun switchGoogleAccount(): Result<Boolean> {
        // The new account first, on purpose: this is the step a player can
        // cancel out of, and it must cost nothing when they do. Only once a
        // usable credential is actually in hand does the old one get given up.
        val newCredential = googleCredential().getOrElse { return Result.failure(it) }
        runCatching { auth.currentUser?.unlink(GoogleAuthProvider.PROVIDER_ID)?.await() }
            .onFailure { Log.w(TAG, "switchGoogleAccount: unlink of old provider failed", it) }
        return runCatching {
            ensureSignedIn()
            auth.currentUser!!.linkWithCredential(newCredential).await()
            false
        }.recoverCatching { error ->
            if (error !is FirebaseAuthUserCollisionException) throw error
            // The chosen account already has its own Firebase identity
            // elsewhere — unavoidably discovered only now, since Firebase
            // refuses to even attempt linking a second google.com credential
            // while this uid still has one attached, collision or not, so
            // the old link had to already be gone before this could be
            // known. Signing into that existing identity is exactly what
            // "Hesap Değiştir" already asked for: the player chose to switch
            // to THIS Google account, and it having a history elsewhere is
            // an implementation detail, not a second decision to put back
            // to them — see switchToExistingAccount for the same migration.
            Log.i(TAG, "switchGoogleAccount: target account collided, signing into its existing identity")
            val migratedFriends = runCatching { friendRepository.prepareFriendMigration() }
                .onFailure { Log.w(TAG, "switchGoogleAccount: prepareFriendMigration failed", it) }
                .getOrDefault(emptyList())
            auth.signInWithCredential(newCredential).await()
            runCatching { friendRepository.adoptMigratedFriends(migratedFriends) }
                .onFailure { Log.w(TAG, "switchGoogleAccount: adoptMigratedFriends failed", it) }
            true
        }.onSuccess {
            refreshAuthState()
        }.onFailure { error ->
            Log.w(TAG, "switchGoogleAccount: linkWithCredential failed", error)
        }
    }

    private fun googleSignInClient(idTokenAudience: String): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(idTokenAudience)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /**
     * Opens the account picker and exchanges the chosen account for a
     * Firebase [AuthCredential].
     *
     * Every exit path is logged. The MIUI reauth bug that drove this whole
     * rewrite (see class doc) looked, from the player's side, identical to
     * an ordinary silent cancel — so a "cancelled" outcome with nothing in
     * Logcat to show for it is exactly the failure mode this cannot afford
     * to repeat.
     */
    private suspend fun googleCredential(): Result<AuthCredential> {
        val clientId = webClientId ?: return Result.failure(
            IllegalStateException("Google Sign-In is not configured for this Firebase project yet")
        )
        return try {
            val client = googleSignInClient(clientId)
            // Clears this app's own cached choice, not the device's account —
            // without it a second attempt (or "switch to existing account")
            // would silently hand back whichever account was used last
            // instead of showing the picker at all.
            runCatching { client.signOut().await() }

            Log.i(TAG, "Launching Google account picker")
            val result = googleSignInLauncher.launch(client.signInIntent)
            Log.i(TAG, "Google account picker returned resultCode=${result.resultCode}")
            if (result.resultCode != Activity.RESULT_OK) {
                // The one place a plain "no account chosen" cancellation is
                // still expected to reach here — dismissing the picker sets
                // no result data at all, which the ApiException branch below
                // has nothing to inspect.
                return Result.failure(LinkFailureException(LinkFailure.Cancelled))
            }

            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val idToken = account.idToken
                ?: return Result.failure(LinkFailureException(LinkFailure.Other("No ID token returned")))
            Result.success(GoogleAuthProvider.getCredential(idToken, null))
        } catch (e: ApiException) {
            Log.w(TAG, "Google sign-in failed: statusCode=${e.statusCode}", e)
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> Result.failure(LinkFailureException(LinkFailure.Cancelled))
                else -> Result.failure(LinkFailureException(LinkFailure.Other(e.message)))
            }
        } catch (e: Exception) {
            // Anything else — including the launcher bridge's own
            // IllegalStateException if no Activity ever bound it — must
            // still leave a trace instead of vanishing into a bare
            // Result.failure the UI resets from with nothing to show.
            Log.w(TAG, "Google sign-in threw unexpectedly: ${e::class.simpleName}", e)
            Result.failure(LinkFailureException(LinkFailure.Other(e.message)))
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
