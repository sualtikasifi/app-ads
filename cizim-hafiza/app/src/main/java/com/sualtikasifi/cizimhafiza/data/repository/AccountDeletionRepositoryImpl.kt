package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.data.local.AppDatabase
import com.sualtikasifi.cizimhafiza.domain.repository.AccountDeletionRepository
import com.sualtikasifi.cizimhafiza.domain.repository.ReauthenticationRequiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * See AccountDeletionRepository for why this exists and why the remote half
 * is best-effort.
 *
 * Firestore has no client-side recursive delete, so each subcollection this
 * app writes under `users/{uid}` is listed explicitly. Adding a new one
 * means adding it here too — a subcollection left out of this list survives
 * the account that owned it.
 */
@Singleton
class AccountDeletionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) : AccountDeletionRepository {

    override suspend fun deleteAccountAndData(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user"))
        val uid = user.uid

        // 1) Remote. Failures here are swallowed on purpose: a rules change,
        //    a lost connection or a document that never existed must not
        //    strand the player with an account they asked to be rid of.
        runCatching { deleteUserTree(uid) }

        // 2) The auth user. This one's failure is real — without it the
        //    account still exists and the player's request was not honoured.
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            // Firebase refuses to delete a user whose sign-in is old. The
            // caller has to send them back through Google sign-in first.
            return Result.failure(ReauthenticationRequiredException())
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // 3) Local, unconditionally and last: by this point there is no
        //    account left for the local data to belong to.
        runCatching { database.clearAllTables() }
        runCatching {
            LOCAL_PREFS.forEach { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        return Result.success(Unit)
    }

    private suspend fun deleteUserTree(uid: String) {
        val userDoc = firestore.collection("users").document(uid)
        USER_SUBCOLLECTIONS.forEach { name ->
            runCatching {
                userDoc.collection(name).get().await().documents.forEach { doc ->
                    runCatching { doc.reference.delete().await() }
                }
            }
        }
        // The friend code is intentionally last and intentionally allowed to
        // fail: firestore.rules currently forbids deleting a friendCodes
        // document (`allow update, delete: if false`), so the mapping is
        // left pointing at a uid that no longer exists. Harmless — every
        // lookup through it dead-ends — but see the note in the account
        // deletion section of firestore.rules if that ever needs cleaning up.
        runCatching {
            val code = userDoc.get().await().getString("friendCode")
            if (code != null) firestore.collection("friendCodes").document(code).delete().await()
        }
        runCatching { userDoc.delete().await() }
    }

    private companion object {
        val USER_SUBCOLLECTIONS = listOf(
            "backup", "friends", "invites", "blockedUsers", "inviteCooldowns", "private"
        )
        val LOCAL_PREFS = listOf(
            "cizim_hafiza_settings",
            "karalak_backup",
            "ad_manager_prefs",
            "karalak_daily_challenge"
        )
    }
}
