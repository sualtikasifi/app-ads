package com.sualtikasifi.cizimhafiza.util

import android.util.Log
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a linked account's cloud backup current without the player ever
 * having to remember to press "Şimdi Yedekle".
 *
 * Before this, [BackupRepository.backupNow] only ever ran on an explicit
 * tap on the Hesap screen — a screen most players never revisit once
 * they've linked once. A phone lost or reset between that one tap and
 * whatever progress came after it would restore to a stale backup, which
 * defeats the entire point of linking in the first place.
 *
 * Follows [SettingsRepository]'s own observable progress fields the same
 * way [WeeklyScorePublisher] follows weekly XP: a StateFlow-backed value
 * changing is itself the trigger, debounced so a whole match's worth of
 * per-word XP collapses into one write a few seconds after the player
 * stops earning, rather than one write per word.
 */
@Singleton
class AutoBackupPublisher @Inject constructor(
    private val authRepository: AuthRepository,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Safe to call repeatedly; only the first call subscribes. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                authRepository.authState,
                settingsRepository.lifetimeXp,
                settingsRepository.nickname,
                settingsRepository.selectedAvatarFrameId,
                settingsRepository.selectedPenSkinId
            ) { authState, _, _, _, _ -> authState }
                .filterIsInstance<AuthState.Linked>()
                .debounce(BACKUP_DEBOUNCE_MS)
                .collect { runBackup() }
        }
    }

    /**
     * Called when the app leaves the foreground — a safety net alongside
     * the debounced trigger above, for the rarer change (an achievement
     * unlocking with no XP attached, a streak freeze) that this class's own
     * observed fields would not otherwise catch on their own.
     */
    fun backupNowIfLinked() {
        if (authRepository.authState.value !is AuthState.Linked) return
        scope.launch { runBackup() }
    }

    private suspend fun runBackup() {
        backupRepository.backupNow().onFailure { Log.w(TAG, "Auto backup failed", it) }
    }

    private companion object {
        const val TAG = "AutoBackupPublisher"

        /** Long enough to sit out a match's per-word XP, short enough to land before the app is closed. */
        const val BACKUP_DEBOUNCE_MS = 10_000L
    }
}
