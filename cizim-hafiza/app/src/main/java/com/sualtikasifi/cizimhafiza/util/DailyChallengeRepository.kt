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
        _state.value = readState()
    }

    /**
     * Records a finished attempt and returns the resulting state.
     *
     * Extends the streak when yesterday was played, and otherwise starts
     * again at 1. Nothing bridges a gap automatically any more: a missed day
     * is recovered deliberately, by watching an ad ([rescueStreak]), and
     * only within [MAX_RESCUE_GAP_DAYS]. The
     * caller is responsible for having checked [DailyChallengeState.isAvailableToday]
     * first — a second call on the same day is ignored rather than
     * double-counting the streak.
     *
     * [xpForStreak] computes the total XP to award from the *actual*
     * resulting streak — not a guess made before a reset is
     * accounted for. Passing a plain number here would occasionally overpay
     * (a caller assuming the streak extends when it's actually about to
     * reset to 1) or, with a rate that climbs by streak tier, credit a tier
     * the streak never really reached.
     */
    fun recordCompletion(correctFlags: List<Boolean>, score: Int, xpForStreak: (Int) -> Int): DailyChallengeState {
        val today = LocalDate.now().toEpochDay()
        val last = lastCompletedEpochDay
        if (last == today) return _state.value

        val gap = if (last < 0) Long.MAX_VALUE else today - last
        val newStreak = if (gap == 1L) currentStreak + 1 else 1
        val xpEarned = xpForStreak(newStreak)

        prefs.edit {
            putLong(KEY_LAST_COMPLETED, today)
            putInt(KEY_CURRENT_STREAK, newStreak)
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
     * Keeps a streak alive across a missed day or two, in exchange for a
     * watched rewarded ad.
     *
     * This replaced a stock of "streak freezes" the player banked in advance
     * and spent silently. Banking never worked as a mechanic here: the count
     * was invisible until it had already been spent, so a player learned the
     * feature existed only by noticing a streak that should have broken
     * hadn't. A deliberate offer at the moment of the break is both clearer
     * and honest — the player chooses to keep it, and knows what it cost.
     *
     * Bounded by [MAX_RESCUE_GAP_DAYS] and to one rescue per day, so it
     * covers a forgotten evening or a weekend away, not a fortnight: a
     * streak an ad can always resurrect is not a streak.
     *
     * Implemented by backdating the last-completed day to yesterday, so
     * today's challenge then extends the restored streak through the normal
     * path in [recordCompletion] with no special case anywhere.
     */
    /**
     * Hides the rescue offer for this app session without spending it.
     * In-memory rather than persisted: the offer expires on its own within
     * [MAX_RESCUE_GAP_DAYS], and someone who dismisses it by reflex should
     * still get it back on their next visit rather than losing a long streak
     * to one stray tap.
     */
    fun dismissRescuePrompt() {
        rescuePromptDismissed = true
        _state.value = readState()
    }

    @Volatile
    private var rescuePromptDismissed = false

    fun rescueStreak(): Boolean {
        val today = LocalDate.now().toEpochDay()
        if (_state.value.rescuableStreak <= 0) return false
        prefs.edit {
            putLong(KEY_LAST_COMPLETED, today - 1)
            putLong(KEY_LAST_RESCUE_DAY, today)
        }
        _state.value = readState()
        return true
    }

    /**
     * Cloud-restore only (see BackupRepositoryImpl): a completion-day/streak
     * pair is only adopted together, and only if the backup's day is at
     * least as recent as what's already stored — pulling in an OLDER backup
     * (e.g. restoring a stale one by mistake) can never un-complete today's
     * already-played challenge or roll the live streak backwards.
     * [bestStreak] is an independent high-water mark and is only ever
     * raised, matching SettingsRepository.restoreIfBetter.
     */
    fun restoreIfBetter(lastCompletedEpochDay: Long, currentStreak: Int, bestStreak: Int) {
        prefs.edit {
            if (lastCompletedEpochDay > this@DailyChallengeRepository.lastCompletedEpochDay) {
                putLong(KEY_LAST_COMPLETED, lastCompletedEpochDay)
                putInt(KEY_CURRENT_STREAK, currentStreak)
            }
            if (bestStreak > this@DailyChallengeRepository.bestStreak) putInt(KEY_BEST_STREAK, bestStreak)
        }
        _state.value = readState()
    }

    private fun readState(): DailyChallengeState {
        val today = LocalDate.now().toEpochDay()
        val last = lastCompletedEpochDay
        // A streak is only still "live" if it was kept today or yesterday;
        // beyond that the stored number is history unless the player buys it
        // back, and showing it as current would be a lie.
        val streakIsLive = last >= 0 && today - last <= 1
        // The stored streak survives a lapse even though the *exposed* one
        // reads zero — that is what makes the rescue offer possible (see
        // rescueStreak). One or two missed days can be bought back; from the
        // third the streak is genuinely over.
        val gap = if (last < 0) Long.MAX_VALUE else today - last
        val rescuable = when {
            rescuePromptDismissed -> 0
            streakIsLive -> 0
            currentStreak <= 0 -> 0
            gap > MAX_RESCUE_GAP_DAYS -> 0
            prefs.getLong(KEY_LAST_RESCUE_DAY, -1L) == today -> 0
            else -> currentStreak
        }
        return DailyChallengeState(
            todayEpochDay = today,
            lastCompletedEpochDay = last,
            currentStreak = if (streakIsLive) currentStreak else 0,
            bestStreak = bestStreak,
            lastResult = readLastResult(),
            rescuableStreak = rescuable
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

    private companion object {
        const val PREFS_NAME = "karalak_daily_challenge"
        const val KEY_LAST_COMPLETED = "last_completed_epoch_day"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_RESULT_DAY = "result_epoch_day"
        const val KEY_RESULT_FLAGS = "result_flags"
        const val KEY_RESULT_SCORE = "result_score"
        const val KEY_RESULT_XP = "result_xp"
        const val KEY_RESULT_STREAK = "result_streak"
        const val KEY_LAST_RESCUE_DAY = "last_rescue_epoch_day"

        /**
         * How stale a break can be and still be bought back: a gap of 3 days
         * means two were missed. From the third missed day the player has
         * genuinely stopped playing daily, and letting an ad undo a week
         * away would make the streak meaningless.
         */
        const val MAX_RESCUE_GAP_DAYS = 3L
    }
}

data class DailyChallengeState(
    val todayEpochDay: Long,
    val lastCompletedEpochDay: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastResult: DailyChallengeResult?,
    /** A recently-lapsed streak the player can still buy back with an ad; 0 when there is nothing to rescue. */
    val rescuableStreak: Int = 0
) {
    val isAvailableToday: Boolean get() = lastCompletedEpochDay != todayEpochDay

    /** Today's finished attempt, or null if today hasn't been played yet. */
    val todayResult: DailyChallengeResult? get() = lastResult?.takeIf { it.epochDay == todayEpochDay }

    /**
     * The streak today's challenge would settle on if it were finished right
     * now — freeze bridging included, mirroring [DailyChallengeRepository.recordCompletion].
     *
     * Exists so the menu can promise the exact streak multiplier the player
     * is about to earn (see XpAwards.dailyStreakMultiplier). Assuming the
     * streak always extends would overstate it for anyone coming back after
     * a gap they have no freezes to cover.
     */
    val streakIfCompletedToday: Int get() {
        if (!isAvailableToday) return currentStreak
        // Nothing bridges a gap on its own any more (see
        // DailyChallengeRepository.recordCompletion): today either continues
        // yesterday's streak or starts a fresh one. A rescue, when taken,
        // has already backdated lastCompletedEpochDay to yesterday, so it
        // needs no special case here.
        val gap = if (lastCompletedEpochDay < 0) Long.MAX_VALUE else todayEpochDay - lastCompletedEpochDay
        return if (gap == 1L) currentStreak + 1 else 1
    }
}
