package com.sualtikasifi.cizimhafiza.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Cloud save of local progress (XP, achievements, streaks, cosmetics) under
 * `users/{uid}/backup/state` — see firestore.rules, owner-only read/write.
 * Only meaningful once the uid is [AuthState.Linked] (see AuthRepository):
 * an anonymous uid does not survive an uninstall, so a backup written under
 * one can never actually be found again from a new install.
 */
interface BackupRepository {

    /** Wall-clock millis of the last successful [backupNow], or null if this device never has. Local-only cache, not a Firestore read. */
    val lastBackupAtMillis: StateFlow<Long?>

    /** Uploads every locally-tracked progress counter/preference to this account's cloud backup, overwriting any previous one. */
    suspend fun backupNow(): Result<Unit>

    /**
     * Suspends automatic backups until [endAccountTransition], for the
     * whole span in which the signed-in account and the local progress on
     * disk may not describe the same player.
     *
     * That span is wider than it looks. It opens the moment a sign-in can
     * change the uid and does not close until the local data has been
     * replaced to match — and in between, this device is signed in as the
     * NEW account while still holding the OLD one's level and name. A
     * backup fired there (AutoBackupPublisher writes on every onPause, with
     * no debounce to hide behind) would upload one player's progress into
     * another player's cloud backup and overwrite it for good.
     *
     * Nesting is safe; the calls are counted, so the guard only lifts when
     * the outermost one ends.
     */
    fun beginAccountTransition()

    fun endAccountTransition()

    /**
     * Called right after the signed-in uid itself changed to a DIFFERENT
     * account (see AuthRepository.signInWithGoogle) — never for an ordinary
     * restore. [restoreLatest]'s merge policy assumes the device's local
     * numbers and the backup describe the same player and would let a
     * higher level survive the switch; here they describe two different
     * players, so the new account's backup REPLACES local progress outright
     * — or, if that account has never backed up before, local progress is
     * reset to a fresh start at level 1. Returns true if a backup was found
     * and restored, false if the account was fresh and progress was reset.
     */
    suspend fun switchToAccount(): Result<Boolean>

    /**
     * Wipes every trace of the signed-out player from this device: lifetime
     * counters, cosmetics, nickname, weekly standing, daily-challenge
     * history, level-map stars, achievements and past games. Sound and
     * language settings, the tutorial flag and the word pool stay — they
     * belong to the phone, not to whoever was signed in.
     *
     * Call ONLY after [backupNow] has already put the outgoing account's
     * progress safely in the cloud (see AuthRepository.signOut's ordering):
     * everything erased here is recoverable exclusively from that backup.
     */
    suspend fun clearLocalProgress(): Result<Unit>
}
