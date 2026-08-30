package com.sualtikasifi.cizimhafiza.data.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
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
    private val auth: FirebaseAuth
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

    private fun deriveState(user: com.google.firebase.auth.FirebaseUser?): AuthState = when {
        user == null -> AuthState.Unknown
        user.isAnonymous -> AuthState.Anonymous
        else -> AuthState.Linked(email = user.email, displayName = user.displayName)
    }

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun linkWithGoogle(activity: Activity): Result<Unit> {
        val firebaseCredential = googleCredential(activity).getOrElse { return Result.failure(it) }
        return runCatching {
            ensureSignedIn()
            auth.currentUser!!.linkWithCredential(firebaseCredential).await()
            Unit
        }.recoverCatching { error ->
            if (error is FirebaseAuthUserCollisionException) throw LinkFailureException(LinkFailure.CredentialAlreadyInUse)
            throw error
        }
    }

    override suspend fun switchToExistingAccount(activity: Activity): Result<Unit> =
        googleCredential(activity).mapCatching { firebaseCredential ->
            auth.signInWithCredential(firebaseCredential).await()
            Unit
        }

    /** Opens the account picker and exchanges the chosen account for a Firebase [com.google.firebase.auth.AuthCredential]. */
    private suspend fun googleCredential(activity: Activity): Result<com.google.firebase.auth.AuthCredential> {
        val clientId = webClientId ?: return Result.failure(
            IllegalStateException("Google Sign-In is not configured for this Firebase project yet")
        )
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            Result.success(GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null))
        } catch (e: GetCredentialCancellationException) {
            Result.failure(LinkFailureException(LinkFailure.Cancelled))
        } catch (e: GetCredentialException) {
            Result.failure(LinkFailureException(LinkFailure.Other(e.message)))
        }
    }
}
