package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Sound/vibration on-off toggles from the Settings screen, backed by SharedPreferences. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    // Display name shown to the opponent in online (friend-vs-friend) rooms.
    // Empty until the player sets one on their first visit to online mode.
    private val _nickname = MutableStateFlow(prefs.getString(KEY_NICKNAME, "") ?: "")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    // Cumulative points across every finished game, solo or online, ever
    // played on this device — never decreases. A pure statistic now that
    // progression runs on XP (see lifetimeXp below). Kept in SharedPreferences
    // rather than Room on purpose: it survives a Room schema migration
    // untouched, unlike a value stored in a table that
    // fallbackToDestructiveMigration() wipes.
    private val _lifetimeScore = MutableStateFlow(prefs.getInt(KEY_LIFETIME_SCORE, 0))
    val lifetimeScore: StateFlow<Int> = _lifetimeScore.asStateFlow()

    // The single progression currency (see domain.model.PlayerLevel). Unlike
    // lifetimeScore this also pays out for turning up — daily challenges and
    // streaks — so the level badge reflects commitment, not just skill.
    private val _lifetimeXp = MutableStateFlow(prefs.getInt(KEY_LIFETIME_XP, 0))
    val lifetimeXp: StateFlow<Int> = _lifetimeXp.asStateFlow()

    // Same idea as lifetimeScore, for the achievement system's "N kelime
    // çizdin" milestones (see domain.model.Achievement) — game_sessions is
    // pruned (see GameSessionDao.pruneOlderThan) so it can't answer "how
    // many words ever", this never-shrinking counter can.
    private val _lifetimeWordsDrawn = MutableStateFlow(prefs.getInt(KEY_LIFETIME_WORDS_DRAWN, 0))
    val lifetimeWordsDrawn: StateFlow<Int> = _lifetimeWordsDrawn.asStateFlow()

    // Further never-shrinking counters behind the longer-horizon achievements
    // (see domain.model.Achievement) — same rationale as lifetimeWordsDrawn:
    // game_sessions is pruned, so it can't answer "ever" questions.
    val lifetimeGamesPlayed: Int get() = prefs.getInt(KEY_LIFETIME_GAMES_PLAYED, 0)
    val lifetimePerfectRounds: Int get() = prefs.getInt(KEY_LIFETIME_PERFECT_ROUNDS, 0)
    val lifetimeOnlineWins: Int get() = prefs.getInt(KEY_LIFETIME_ONLINE_WINS, 0)
    val bestStreak: Int get() = prefs.getInt(KEY_BEST_STREAK, 0)

    // Which AvatarFrame ring the player has chosen to wear (see
    // domain.model.AvatarFrame.resolve) — persisted by the enum constant's
    // own name, same convention as UnlockedAchievementEntity, so renaming a
    // constant would strand this pref on a frame that no longer resolves.
    // Starts on AvatarFrame.DEFAULT (the level-1 frame) for every install.
    private val _selectedAvatarFrameId = MutableStateFlow(prefs.getString(KEY_SELECTED_AVATAR_FRAME, AvatarFrame.DEFAULT.name) ?: AvatarFrame.DEFAULT.name)
    val selectedAvatarFrameId: StateFlow<String> = _selectedAvatarFrameId.asStateFlow()

    // Daily "come back and play" reminder (see notifications/DailyEngagementWorker.kt).
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // Streak bookkeeping for the reminder worker. Not exposed as StateFlow —
    // only ever read by the background worker, never observed by the UI.
    val lastPlayedEpochDay: Long get() = prefs.getLong(KEY_LAST_PLAYED_EPOCH_DAY, -1L)
    val currentStreak: Int get() = prefs.getInt(KEY_CURRENT_STREAK, 0)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUND, enabled) }
        _soundEnabled.value = enabled
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VIBRATION, enabled) }
        _vibrationEnabled.value = enabled
    }

    fun setNickname(name: String) {
        val trimmed = name.trim()
        prefs.edit { putString(KEY_NICKNAME, trimmed) }
        _nickname.value = trimmed
    }

    fun setSelectedAvatarFrame(frame: AvatarFrame) {
        prefs.edit { putString(KEY_SELECTED_AVATAR_FRAME, frame.name) }
        _selectedAvatarFrameId.value = frame.name
    }

    fun addScore(points: Int) {
        val updated = _lifetimeScore.value + points
        prefs.edit { putInt(KEY_LIFETIME_SCORE, updated) }
        _lifetimeScore.value = updated
    }

    fun addWordsDrawn(count: Int) {
        val updated = _lifetimeWordsDrawn.value + count
        prefs.edit { putInt(KEY_LIFETIME_WORDS_DRAWN, updated) }
        _lifetimeWordsDrawn.value = updated
    }

    /** Adds to the progression currency. See domain.model.XpAwards for what each action is worth. */
    fun addXp(amount: Int) {
        if (amount <= 0) return
        val updated = _lifetimeXp.value + amount
        prefs.edit { putInt(KEY_LIFETIME_XP, updated) }
        _lifetimeXp.value = updated
    }

    /**
     * Bumps the per-finished-game lifetime tallies the achievement catalog
     * reads. Called once per saved game (solo or online) alongside
     * [addScore]/[addWordsDrawn] — see GameRepositoryImpl.finishSaving.
     */
    fun recordFinishedGame(wasPerfectRound: Boolean, wasOnlineWin: Boolean) {
        prefs.edit {
            putInt(KEY_LIFETIME_GAMES_PLAYED, lifetimeGamesPlayed + 1)
            if (wasPerfectRound) putInt(KEY_LIFETIME_PERFECT_ROUNDS, lifetimePerfectRounds + 1)
            if (wasOnlineWin) putInt(KEY_LIFETIME_ONLINE_WINS, lifetimeOnlineWins + 1)
        }
    }

    /**
     * One-time migration for devices that earned a rank before progression
     * moved from raw score to XP. Grants exactly enough XP to land at the
     * floor of the tier that score had already unlocked, so nobody opens the
     * update to find themselves demoted to Karalamacı.
     */
    fun seedLifetimeXpFromLegacyScore(legacyScore: Int) {
        if (prefs.contains(KEY_LIFETIME_XP)) return
        // The old score thresholds, paired with the level each tier now starts at.
        val legacyTiers = listOf(0 to 1, 1000 to 20, 3000 to 40, 5000 to 60, 10000 to 80, 25000 to PlayerLevel.MAX_LEVEL)
        val earnedLevel = legacyTiers.last { legacyScore >= it.first }.second
        val seeded = PlayerLevel.totalXpForLevel(earnedLevel)
        prefs.edit { putInt(KEY_LIFETIME_XP, seeded) }
        _lifetimeXp.value = seeded
    }

    /** One-time seed from surviving local game history, only if no lifetime score has been recorded yet. */
    fun seedLifetimeScoreIfAbsent(fallbackScore: Int) {
        if (prefs.contains(KEY_LIFETIME_SCORE)) return
        prefs.edit { putInt(KEY_LIFETIME_SCORE, fallbackScore) }
        _lifetimeScore.value = fallbackScore
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
        _notificationsEnabled.value = enabled
    }

    // False until the first-run tutorial (see presentation/tutorial/) has been
    // played or skipped — decides the app's start destination on launch.
    var tutorialCompleted: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_COMPLETED, value) }

    // Guards the one-time automatic permission prompt in MainActivity so it
    // only ever fires on a device's very first launch, not every cold start.
    var notificationPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, value) }

    /**
     * Called whenever a game (solo or online) finishes. Extends the streak by
     * one if the player last played yesterday, leaves it alone if they've
     * already played today, and otherwise resets it to a fresh streak of 1.
     */
    fun updateStreakOnPlay() {
        val today = LocalDate.now().toEpochDay()
        val newStreak = when (today - lastPlayedEpochDay) {
            0L -> currentStreak
            1L -> currentStreak + 1
            else -> 1
        }
        prefs.edit {
            putLong(KEY_LAST_PLAYED_EPOCH_DAY, today)
            putInt(KEY_CURRENT_STREAK, newStreak)
            // High-water mark, so a "longest streak" achievement stays earned
            // after the active streak resets (currentStreak drops back to 1).
            if (newStreak > bestStreak) putInt(KEY_BEST_STREAK, newStreak)
        }
    }

    private companion object {
        const val PREFS_NAME = "cizim_hafiza_settings"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_NICKNAME = "online_nickname"
        const val KEY_SELECTED_AVATAR_FRAME = "selected_avatar_frame"
        const val KEY_LIFETIME_SCORE = "lifetime_score"
        const val KEY_LIFETIME_XP = "lifetime_xp"
        const val KEY_LIFETIME_WORDS_DRAWN = "lifetime_words_drawn"
        const val KEY_LIFETIME_GAMES_PLAYED = "lifetime_games_played"
        const val KEY_LIFETIME_PERFECT_ROUNDS = "lifetime_perfect_rounds"
        const val KEY_LIFETIME_ONLINE_WINS = "lifetime_online_wins"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LAST_PLAYED_EPOCH_DAY = "last_played_epoch_day"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
