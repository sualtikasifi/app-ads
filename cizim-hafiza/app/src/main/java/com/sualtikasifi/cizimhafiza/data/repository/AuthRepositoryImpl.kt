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
    private val googleSignInLauncher: GoogleSignInLauncher
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

    private fun deriveState(user: FirebaseUser?): AuthState = when {
        user == null -> AuthState.Unknown
        user.isAnonymous -> AuthState.Anonymous
        else -> AuthState.Linked(email = user.email, displayName = user.displayName)
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
            auth.signInWithCredential(firebaseCredential).await()
            Unit
        }

    private fun googleSignInClient(idTokenAudience: String): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(idTokenAudience)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /** Opens the account picker and exchanges the chosen account for a Firebase [AuthCredential]. */
    private suspend fun googleCredential(): Result<AuthCredential> {
        val clientId = webClientId ?: return Result.failure(
            IllegalStateException("Google Sign-In is not configured for this Firebase project yet")
        )
        val client = googleSignInClient(clientId)
        // Clears this app's own cached choice, not the device's account —
        // without it a second attempt (or "switch to existing account")
        // would silently hand back whichever account was used last instead
        // of showing the picker at all.
        runCatching { client.signOut().await() }

        val result = googleSignInLauncher.launch(client.signInIntent)
        if (result.resultCode != Activity.RESULT_OK) {
            // The one place a plain "no account chosen" cancellation is
            // still expected to reach here — dismissing the picker sets no
            // result data at all, which the ApiException branch below has
            // nothing to inspect.
            return Result.failure(LinkFailureException(LinkFailure.Cancelled))
        }

        return try {
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
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
