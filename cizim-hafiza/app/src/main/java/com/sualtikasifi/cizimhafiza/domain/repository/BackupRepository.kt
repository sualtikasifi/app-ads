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

    /** True if this account has a cloud backup at all (regardless of whether it's ever been restored on this device). */
    suspend fun hasRemoteBackup(): Boolean

    /**
     * Pulls this account's cloud backup down and merges it into local
     * progress (see SettingsRepository/DailyChallengeRepository.restoreIfBetter
     * for the never-regress merge policy). Returns false if there was
     * nothing to restore.
     */
    suspend fun restoreLatest(): Result<Boolean>

    /**
     * Called right after the signed-in uid itself changed to a DIFFERENT
     * account (AuthRepository.switchToExistingAccount, or
     * switchGoogleAccount's collision fallback) — never for an ordinary
     * restore. [restoreLatest]'s merge policy assumes the device's local
     * numbers and the backup describe the same player and would let a
     * higher level survive the switch; here they describe two different
     * players, so the new account's backup REPLACES local progress outright
     * — or, if that account has never backed up before, local progress is
     * reset to a fresh start. Returns true if a backup was found and
     * restored, false if the account was fresh and progress was reset.
     */
    suspend fun switchToAccount(): Result<Boolean>
}
