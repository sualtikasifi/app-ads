package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import androidx.core.content.edit
import com.sualtikasifi.cizimhafiza.domain.model.DailyChallengeResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that persists about the daily challenge: whether today's is
 * done, how long the streak is, and how many misses the player can still
 * absorb.
 *
 * Kept in SharedPreferences alongside [SettingsRepository]'s other lifetime
 * counters for the same reason those are — Room runs with
 * fallbackToDestructiveMigration(), and a streak someone has held for two
 * months must not be a schema change away from disappearing.
 *
 * Deliberately separate from [SettingsRepository]'s `currentStreak`, which
 * counts *any* day the player opened a game. The daily streak is the one
 * with something at stake, so conflating the two would let a casual solo
 * game silently keep a "daily" streak alive.
 */
@Singleton
class DailyChallengeRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<DailyChallengeState> = _state.asStateFlow()

    /** Re-reads the stored state; call when the day may have rolled over while the app sat open. */
    fun refresh() {
        grantMonthlyFreezesIfDue()
        _state.value = readState()
    }

    /**
     * Records a finished attempt and returns the resulting state.
     *
     * Extends the streak when yesterday was played, spends freezes to bridge
     * a gap when there are enough, and otherwise starts again at 1. The
     * caller is responsible for having checked [DailyChallengeState.isAvailableToday]
     * first — a second call on the same day is ignored rather than
     * double-counting the streak.
     *
     * [xpForStreak] computes the total XP to award from the *actual*
     * resulting streak — not a guess made before a freeze or a reset is
     * accounted for. Passing a plain number here would occasionally overpay
     * (a caller assuming the streak extends when it's actually about to
     * reset to 1) or, with a rate that climbs by streak tier, credit a tier
     * the streak never really reached.
     */
    fun recordCompletion(correctFlags: List<Boolean>, score: Int, xpForStreak: (Int) -> Int): DailyChallengeState {
        grantMonthlyFreezesIfDue()
        val today = LocalDate.now().toEpochDay()
        val last = lastCompletedEpochDay
        if (last == today) return _state.value

        val gap = if (last < 0) Long.MAX_VALUE else today - last
        val missedDays = (gap - 1).coerceAtLeast(0L)
        var freezes = freezesRemaining
        val newStreak = when {
            gap == 1L -> currentStreak + 1
            // A freeze covers one missed day. Spending them only makes sense
            // if they cover the whole gap — half a bridge still drops the streak.
            missedDays in 1..freezes.toLong() -> {
                freezes -= missedDays.toInt()
                currentStreak + 1
            }
            else -> 1
        }
        val xpEarned = xpForStreak(newStreak)

        prefs.edit {
            putLong(KEY_LAST_COMPLETED, today)
            putInt(KEY_CURRENT_STREAK, newStreak)
            putInt(KEY_FREEZES, freezes)
            if (newStreak > bestStreak) putInt(KEY_BEST_STREAK, newStreak)
            putLong(KEY_RESULT_DAY, today)
            putString(KEY_RESULT_FLAGS, correctFlags.joinToString(",") { if (it) "1" else "0" })
            putInt(KEY_RESULT_SCORE, score)
            putInt(KEY_RESULT_XP, xpEarned)
            putInt(KEY_RESULT_STREAK, newStreak)
        }
        _state.value = readState()
        return _state.value
    }

    /**
     * Grants one extra freeze in exchange for a watched rewarded ad.
     *
     * Capped at [MAX_FREEZES] rather than unlimited: a freeze the player can
     * mint on demand isn't insurance any more, it's an off switch for the
     * streak mechanic, and a streak nothing can break stops being worth
     * protecting. Two banked on top of the monthly allowance is enough to
     * cover a holiday without making the number meaningless.
     *
     * Returns false when already at the cap, so the caller can decline to
     * show an ad the player would get nothing for.
     */
    fun grantFreezeFromAd(): Boolean {
        grantMonthlyFreezesIfDue()
        if (freezesRemaining >= MAX_FREEZES) return false
        prefs.edit { putInt(KEY_FREEZES, freezesRemaining + 1) }
        _state.value = readState()
        return true
    }

    /**
     * Restores a streak that has *just* lapsed, in exchange for a watched
     * rewarded ad — the "streak repair" every long-running daily feature
     * ends up needing.
     *
     * Freezes are spent automatically and silently at completion time, which
     * means the player only ever finds out a streak broke after it is far
     * too late to do anything. That moment — opening the app to find a
     * 60-day streak reading zero — is the single most common point at which
     * people stop coming back. Repair gives them one deliberate way out.
     *
     * Guarded on three things so it stays meaningful: the break must be
     * recent ([MAX_REPAIR_GAP_DAYS]), the streak must have been long enough
     * to be worth an ad ([MIN_REPAIRABLE_STREAK]), and only one repair is
     * allowed per day so it cannot be used to paper over a week of absence.
     *
     * Implemented by backdating the last-completed day to yesterday: today's
     * challenge is then still unplayed and extends the restored streak
     * normally, with no special case anywhere in [recordCompletion].
     */
    fun repairStreak(): Boolean {
        val today = LocalDate.now().toEpochDay()
        if (_state.value.repairableStreak <= 0) return false
        prefs.edit {
            putLong(KEY_LAST_COMPLETED, today - 1)
            putLong(KEY_LAST_REPAIR_DAY, today)
        }
        _state.value = readState()
        return true
    }

    /**
     * Cloud-restore only (see BackupRepositoryImpl): adopts a backup's
     * streak/freeze numbers, but a completion-day/streak pair is only
     * adopted together, and only if the backup's day is at least as recent
     * as what's already stored — pulling in an OLDER backup (e.g. restoring
     * a stale one by mistake) can never un-complete today's already-played
     * challenge or roll the live streak backwards. [bestStreak] and
     * [freezesRemaining] are independent high-water marks and are each only
     * ever raised, never lowered, matching SettingsRepository.restoreIfBetter.
     */
    fun restoreIfBetter(lastCompletedEpochDay: Long, currentStreak: Int, bestStreak: Int, freezesRemaining: Int) {
        grantMonthlyFreezesIfDue()
        prefs.edit {
            if (lastCompletedEpochDay > this@DailyChallengeRepository.lastCompletedEpochDay) {
                putLong(KEY_LAST_COMPLETED, lastCompletedEpochDay)
                putInt(KEY_CURRENT_STREAK, currentStreak)
            }
            if (bestStreak > this@DailyChallengeRepository.bestStreak) putInt(KEY_BEST_STREAK, bestStreak)
            if (freezesRemaining > this@DailyChallengeRepository.freezesRemaining) putInt(KEY_FREEZES, freezesRemaining)
        }
        _state.value = readState()
    }

    /**
     * Tops the player back up to [MONTHLY_FREEZES] at the start of each
     * calendar month. Granted lazily on read rather than by a scheduled job:
     * a freeze only ever matters at the moment a completion is recorded, and
     * a background worker that failed to run would silently cost someone
     * their streak.
     */
    private fun grantMonthlyFreezesIfDue() {
        val thisMonth = YearMonth.now().let { it.year * 12L + it.monthValue }
        if (prefs.getLong(KEY_FREEZE_MONTH, -1L) == thisMonth) return
        prefs.edit {
            putLong(KEY_FREEZE_MONTH, thisMonth)
            putInt(KEY_FREEZES, MONTHLY_FREEZES)
        }
    }

    private fun readState(): DailyChallengeState {
        val today = LocalDate.now().toEpochDay()
        val last = lastCompletedEpochDay
        // A streak is only still "live" if it was kept today or yesterday;
        // beyond that the stored number is history until freezes are spent
        // on it, and showing it as current would be a lie.
        val streakIsLive = last >= 0 && today - last <= 1
        // The stored streak survives a lapse even though the *exposed* one
        // reads zero — that is what makes an offer to restore it possible
        // (see repairStreak). Only a recent, substantial break qualifies,
        // and only one repair a day.
        val gap = if (last < 0) Long.MAX_VALUE else today - last
        val repairable = when {
            streakIsLive -> 0
            currentStreak < MIN_REPAIRABLE_STREAK -> 0
            gap > MAX_REPAIR_GAP_DAYS -> 0
            prefs.getLong(KEY_LAST_REPAIR_DAY, -1L) == today -> 0
            else -> currentStreak
        }
        return DailyChallengeState(
            todayEpochDay = today,
            lastCompletedEpochDay = last,
            currentStreak = if (streakIsLive) currentStreak else 0,
            bestStreak = bestStreak,
            freezesRemaining = freezesRemaining,
            lastResult = readLastResult(),
            repairableStreak = repairable,
            canEarnFreeze = freezesRemaining < MAX_FREEZES
        )
    }

    private fun readLastResult(): DailyChallengeResult? {
        val day = prefs.getLong(KEY_RESULT_DAY, -1L)
        if (day < 0) return null
        val flags = prefs.getString(KEY_RESULT_FLAGS, "").orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .map { it == "1" }
        return DailyChallengeResult(
            epochDay = day,
            correctFlags = flags,
            score = prefs.getInt(KEY_RESULT_SCORE, 0),
            streakAfter = prefs.getInt(KEY_RESULT_STREAK, 0),
            xpEarned = prefs.getInt(KEY_RESULT_XP, 0)
        )
    }

    private val lastCompletedEpochDay: Long get() = prefs.getLong(KEY_LAST_COMPLETED, -1L)
    private val currentStreak: Int get() = prefs.getInt(KEY_CURRENT_STREAK, 0)
    private val bestStreak: Int get() = prefs.getInt(KEY_BEST_STREAK, 0)
    private val freezesRemaining: Int get() = prefs.getInt(KEY_FREEZES, MONTHLY_FREEZES)

    private companion object {
        const val PREFS_NAME = "karalak_daily_challenge"
        const val KEY_LAST_COMPLETED = "last_completed_epoch_day"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_FREEZES = "freezes_remaining"
        const val KEY_FREEZE_MONTH = "freeze_grant_month"
        const val KEY_RESULT_DAY = "result_epoch_day"
        const val KEY_RESULT_FLAGS = "result_flags"
        const val KEY_RESULT_SCORE = "result_score"
        const val KEY_RESULT_XP = "result_xp"
        const val KEY_RESULT_STREAK = "result_streak"
        const val KEY_LAST_REPAIR_DAY = "last_repair_epoch_day"

        /**
         * Two misses a month. Enough that one bad week doesn't end a long
         * streak — which is the single biggest cause of players walking away
         * from a daily feature — without making the streak meaningless.
         */
        const val MONTHLY_FREEZES = 2

        /**
         * Ceiling on banked freezes, including any earned from a rewarded ad
         * (see grantFreezeFromAd). A stock the player can top up without
         * limit turns the streak into something that can never break, which
         * is the same as not having one.
         */
        const val MAX_FREEZES = 4

        /**
         * A streak has to be worth something before it is worth an ad to get
         * back — restoring a two-day streak is not a moment anyone cares
         * about, and offering it cheapens the ones that do matter.
         */
        const val MIN_REPAIRABLE_STREAK = 3

        /**
         * How stale a break can be and still be repairable. Beyond this the
         * player has genuinely stopped playing daily, and letting an ad undo
         * a week away would make the streak meaningless.
         */
        const val MAX_REPAIR_GAP_DAYS = 3L
    }
}

data class DailyChallengeState(
    val todayEpochDay: Long,
    val lastCompletedEpochDay: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    val freezesRemaining: Int,
    val lastResult: DailyChallengeResult?,
    /** A recently-lapsed streak the player can still buy back with an ad; 0 when there is nothing to restore. */
    val repairableStreak: Int = 0,
    /** False once the freeze stock is at its cap — no point offering an ad for a reward that cannot be granted. */
    val canEarnFreeze: Boolean = true
) {
    val isAvailableToday: Boolean get() = lastCompletedEpochDay != todayEpochDay

    /** Today's finished attempt, or null if today hasn't been played yet. */
    val todayResult: DailyChallengeResult? get() = lastResult?.takeIf { it.epochDay == todayEpochDay }
}
