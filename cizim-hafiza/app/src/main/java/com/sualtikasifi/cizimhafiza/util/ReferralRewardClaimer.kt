package com.sualtikasifi.cizimhafiza.util

import android.util.Log
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claims whatever XP a Blaze-free cron script has left on this account's
 * private/pendingRewards document — right now, the sole source is the
 * referral reward (see functions/src/index.ts's runGrantReferralRewards):
 * once someone this device invited reaches level 5, the cron drops one
 * entry there for this device to pick up.
 *
 * A cron can't hand out XP directly: [SettingsRepository]'s lifetimeXp is
 * this device's own local counter, and [LeagueScorePublisher] republishes
 * it onto the public profile on every change — a server-side write to that
 * same profile would just be overwritten on the next publish. Claiming has
 * to happen here, applied through [SettingsRepository.addXp] like any other
 * XP gain, so it durably becomes part of the player's own progression
 * instead of a number that only ever lived in Firestore.
 *
 * Runs once at app start (self-healing, same reasoning as
 * LeagueScorePublisher: a claim lost to a dead connection is simply retried
 * next launch) rather than on a live listener — nothing about a pending
 * reward is urgent enough to need it applied mid-session.
 */
@Singleton
class ReferralRewardClaimer @Inject constructor(
    private val friendRepository: FriendRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val _lastClaimedXp = MutableStateFlow(0)
    /** The amount just claimed, for a one-time "kazandın!" toast — see MainMenuViewModel. */
    val lastClaimedXp: StateFlow<Int> = _lastClaimedXp.asStateFlow()

    /** Safe to call repeatedly; only the first call actually claims. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            runCatching { friendRepository.claimPendingRewards() }
                .onSuccess { amount ->
                    if (amount > 0) {
                        settingsRepository.addXp(amount)
                        _lastClaimedXp.value = amount
                    }
                }
                .onFailure { Log.w(TAG, "Pending reward claim failed", it) }
        }
    }

    fun consumeClaimedNotice() {
        _lastClaimedXp.value = 0
    }

    private companion object {
        const val TAG = "ReferralRewardClaimer"
    }
}
