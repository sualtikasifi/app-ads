package com.sualtikasifi.cizimhafiza.domain.repository

/**
 * Erases this player's account: everything stored about them in Firestore,
 * everything stored on this device, and the Firebase user itself.
 *
 * Required by Google Play for any app that lets people create an account —
 * an in-app route to delete the account and the data attached to it, not
 * merely to sign out. Karalak links a Google account (see
 * [AuthRepository.linkWithGoogle]) and keeps a profile, a friends list, a
 * cloud backup and a weekly-league entry under `users/{uid}`, so signing
 * out alone left all of that behind with no way for the player to reach it.
 *
 * Deliberately best-effort on the remote side and unconditional on the
 * local side: a document this account can no longer prove ownership of is
 * worse than one that lingers, so the auth user is deleted last, and local
 * data is wiped even if a remote delete failed.
 */
interface AccountDeletionRepository {
    /**
     * @return failure only when the account itself could not be deleted.
     *   A partial remote cleanup still counts as success — see the class doc.
     */
    suspend fun deleteAccountAndData(): Result<Unit>
}

/** The Firebase user was signed in too long ago for a delete to be allowed without signing in again. */
class ReauthenticationRequiredException : Exception()
