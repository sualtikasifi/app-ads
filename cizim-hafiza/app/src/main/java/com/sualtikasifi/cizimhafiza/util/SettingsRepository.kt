package com.sualtikasifi.cizimhafiza.util

import android.content.Context
import android.content.SharedPreferences
import com.sualtikasifi.cizimhafiza.R
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.model.LeaguePeriod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Sound/vibration on-off toggles from the Settings screen, backed by SharedPreferences. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * What to call a player who never typed a nickname — a localised string,
     * not the hard-coded "Oyuncu" it used to be in ten separate call sites.
     * That name is shown to OTHER players (lobby, result table, league), so
     * an English player with no nickname was appearing to everyone, in every
     * language, under a Turkish word.
     */
    val nicknameOrDefault: String
        get() = nickname.value.trim().ifBlank { context.getString(R.string.default_nickname) }

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    /**
     * The background music, separately from [soundEnabled].
     *
     * Two switches rather than one because they answer different questions.
     [soundEnabled] is the master — turning it off silences the app
     * completely, music included — while this one exists for the far more
     * common case of wanting the game's own feedback sounds but not a
     * soundtrack, and it is what the in-game speaker button toggles (see
     * DrawingScreen/GuessScreen's top bar) without touching the master.
     */
    private val _musicEnabled = MutableStateFlow(prefs.getBoolean(KEY_MUSIC, true))
    val musicEnabled: StateFlow<Boolean> = _musicEnabled.asStateFlow()

    fun setMusicEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_MUSIC, enabled) }
        _musicEnabled.value = enabled
    }

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

    // The cosmetic pen the player draws with (see domain.model.PenSkin) —
    // same persist-by-enum-name convention, and the same caveat: renaming a
    // constant strands this pref on a pen that no longer resolves.
    private val _selectedPenSkinId = MutableStateFlow(prefs.getString(KEY_SELECTED_PEN_SKIN, PenSkin.DEFAULT.name) ?: PenSkin.DEFAULT.name)
    val selectedPenSkinId: StateFlow<String> = _selectedPenSkinId.asStateFlow()

    // League prizes this account has actually won (see
    // domain.model.LeagueReward). Stored by reward id, the same
    // persist-by-stable-identifier convention as the two selections above.
    //
    // This is the ONLY record that a league cosmetic was earned, so it has
    // to survive a reinstall — it is carried in the cloud backup
    // (ProgressSnapshot.earnedLeagueRewardIds) for exactly that reason. A
    // prize that vanished with the app would be worse than no prize.
    private val _earnedLeagueRewardIds = MutableStateFlow(loadEarnedLeagueRewardIds())
    val earnedLeagueRewardIds: StateFlow<Set<String>> = _earnedLeagueRewardIds.asStateFlow()

    // How many times each online-lobby chat phrase (see
    // presentation.online.PRESET_PHRASES) has actually been sent from this
    // device — lets the "Bir şey söyle" sheet float a player's own most-used
    // phrases to the top instead of showing the same fixed catalog order to
    // everyone. Keyed by the phrase's own stable key, same convention as
    // KEY_SELECTED_AVATAR_FRAME storing AvatarFrame by name.
    private val _phraseUsageCounts = MutableStateFlow(loadPhraseUsageCounts())
    val phraseUsageCounts: StateFlow<Map<String, Int>> = _phraseUsageCounts.asStateFlow()

    // Same idea as [phraseUsageCounts], for the quick-send emoji row (see
    // presentation.online.EMOJI_CATALOG) — keyed by each PresetReaction's
    // own stable key, not the emoji glyph itself, so the map stays plain
    // ASCII regardless of which emoji it's counting.
    private val _emojiUsageCounts = MutableStateFlow(loadEmojiUsageCounts())
    val emojiUsageCounts: StateFlow<Map<String, Int>> = _emojiUsageCounts.asStateFlow()

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

    /**
     * Whether this account's name was ever chosen by the player, as opposed
     * to being filled in from their Google account.
     *
     * Read by [ProfileNameSynchronizer], which fills a blank name from
     * Google. Without this it re-filled ANY blank, at any moment — so
     * clearing the field to type a new name put the old one back before the
     * first new character arrived, on every screen with a nickname field.
     * Once a player has named themselves, a name they then delete is a
     * deliberately empty field, not one waiting to be helped.
     */
    var hasChosenNickname: Boolean
        get() = prefs.getBoolean(KEY_NICKNAME_CHOSEN, false)
        private set(value) = prefs.edit { putBoolean(KEY_NICKNAME_CHOSEN, value) }

    fun setNickname(name: String) {
        val trimmed = name.trim()
        prefs.edit { putString(KEY_NICKNAME, trimmed) }
        _nickname.value = trimmed
        // Typing one character counts: from that keystroke on, this field
        // belongs to the player.
        if (trimmed.isNotEmpty()) hasChosenNickname = true
    }

    fun setSelectedAvatarFrame(frame: AvatarFrame) {
        prefs.edit { putString(KEY_SELECTED_AVATAR_FRAME, frame.name) }
        _selectedAvatarFrameId.value = frame.name
    }

    fun setSelectedPenSkin(skin: PenSkin) {
        prefs.edit { putString(KEY_SELECTED_PEN_SKIN, skin.name) }
        _selectedPenSkinId.value = skin.name
    }

    /**
     * Records a league prize as won. Idempotent — the same week's award is
     * read from the published table on every league open, so this is called
     * again and again for a prize already held.
     *
     * Returns true only the first time, which is what lets the caller show
     * the "you won" card exactly once instead of on every visit.
     */
    fun grantLeagueReward(rewardId: String): Boolean {
        if (rewardId.isBlank() || rewardId in _earnedLeagueRewardIds.value) return false
        val updated = _earnedLeagueRewardIds.value + rewardId
        prefs.edit { putString(KEY_EARNED_LEAGUE_REWARDS, Json.encodeToString(updated)) }
        _earnedLeagueRewardIds.value = updated
        return true
    }

    private fun loadEarnedLeagueRewardIds(): Set<String> {
        val stored = prefs.getString(KEY_EARNED_LEAGUE_REWARDS, null) ?: return emptySet()
        // A prefs value this device cannot parse is not worth crashing over,
        // and there is nothing to recover from it either.
        return runCatching { Json.decodeFromString<Set<String>>(stored) }.getOrDefault(emptySet())
    }

    /** Bumps [phraseUsageCounts] for one chat phrase — called every time it's actually sent (see OnlineGameRepositoryImpl.sendReaction). */
    fun recordPhraseUsed(key: String) {
        val updated = _phraseUsageCounts.value + (key to (_phraseUsageCounts.value[key] ?: 0) + 1)
        prefs.edit { putString(KEY_PHRASE_USAGE_COUNTS, Json.encodeToString(updated)) }
        _phraseUsageCounts.value = updated
    }

    private fun loadPhraseUsageCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_PHRASE_USAGE_COUNTS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    /** Bumps [emojiUsageCounts] for one quick-send emoji — called every time it's actually sent (see OnlineGameRepositoryImpl.sendReaction). */
    fun recordEmojiUsed(key: String) {
        val updated = _emojiUsageCounts.value + (key to (_emojiUsageCounts.value[key] ?: 0) + 1)
        prefs.edit { putString(KEY_EMOJI_USAGE_COUNTS, Json.encodeToString(updated)) }
        _emojiUsageCounts.value = updated
    }

    private fun loadEmojiUsageCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_EMOJI_USAGE_COUNTS, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
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
    /**
     * Takes XP back after a rejected round — the ONLY path in the app that
     * lowers it.
     *
     * The level is not stored, it is derived from this number
     * (PlayerLevel.levelForXp), so it follows on its own and every screen
     * reading the flow updates with it. The period total comes down too:
     * leaving it would let a rejected round keep winning the league.
     *
     * Floored at zero and committed durably rather than with apply(): the
     * record of having applied a penalty is written separately, and a
     * half-written pair would either lose the penalty or repeat it.
     */
    fun revokeXp(amount: Int) {
        if (amount <= 0) return
        val updated = (_lifetimeXp.value - amount).coerceAtLeast(0)
        val period = (_periodXp.value - amount).coerceAtLeast(0)
        prefs.edit(commit = true) {
            putInt(KEY_LIFETIME_XP, updated)
            putInt(KEY_PERIOD_XP, period)
        }
        _lifetimeXp.value = updated
        _periodXp.value = period
    }

    /**
     * How many moderation penalties this device has applied.
     *
     * Account-scoped and carried into the backup snapshot, because it is what
     * lets the restore guards tell a penalty apart from data loss — see
     * ProgressSnapshot.penaltiesApplied.
     */
    var penaltiesApplied: Int
        get() = prefs.getInt(KEY_PENALTIES_APPLIED, 0)
        set(value) {
            prefs.edit(commit = true) { putInt(KEY_PENALTIES_APPLIED, value) }
        }

    fun addXp(amount: Int) {
        if (amount <= 0) return
        val updated = _lifetimeXp.value + amount
        prefs.edit { putInt(KEY_LIFETIME_XP, updated) }
        _lifetimeXp.value = updated
        addPeriodXp(amount)
    }

    /**
     * Last calendar day a Hızlı Eşleş (Quick Match) round's daily 2x-XP
     * bonus was actually claimed (see GameConstants.
     * QUICK_MATCH_DAILY_BONUS_MULTIPLIER) — read-only here; only
     * [claimQuickMatchDailyBonus] advances it.
     */
    val lastQuickMatchEpochDay: Long get() = prefs.getLong(KEY_LAST_QUICK_MATCH_EPOCH_DAY, -1L)

    /**
     * Marks today as having paid the Quick Match daily bonus. Idempotent
     * within a day: returns false (and writes nothing) if today was already
     * claimed. Called once, from GameViewModel.finishGame(), only for a
     * quick match round that actually finished — a round started and
     * abandoned never spends the day's bonus.
     */
    fun claimQuickMatchDailyBonus(): Boolean {
        val today = LocalDate.now().toEpochDay()
        if (lastQuickMatchEpochDay == today) return false
        prefs.edit { putLong(KEY_LAST_QUICK_MATCH_EPOCH_DAY, today) }
        return true
    }

    // --- League period (see domain.model.LeaguePeriod) ---

    /**
     * XP earned since the first of the month. Rolls over lazily on read and
     * write rather than by a scheduled job: a worker that failed to fire
     * would carry last month's total into the new table, which is far worse
     * than computing the boundary on demand from the date.
     */
    private val _periodXp = MutableStateFlow(readPeriodXp())
    val periodXp: StateFlow<Int> = _periodXp.asStateFlow()

    private fun readPeriodXp(): Int {
        val currentPeriod = LeaguePeriod.periodIdFor(LocalDate.now())
        if (prefs.getLong(KEY_PERIOD_XP_PERIOD, -1L) != currentPeriod) return 0
        return prefs.getInt(KEY_PERIOD_XP, 0)
    }

    private fun addPeriodXp(amount: Int) {
        val currentPeriod = LeaguePeriod.periodIdFor(LocalDate.now())
        val storedPeriod = prefs.getLong(KEY_PERIOD_XP_PERIOD, -1L)
        val base = if (storedPeriod == currentPeriod) prefs.getInt(KEY_PERIOD_XP, 0) else 0
        val updated = base + amount
        prefs.edit {
            putLong(KEY_PERIOD_XP_PERIOD, currentPeriod)
            putInt(KEY_PERIOD_XP, updated)
        }
        _periodXp.value = updated
    }

    /**
     * What LeagueScorePublisher last successfully wrote onto the public
     * profile. Persisted rather than held in memory so relaunching the app
     * with nothing new to say costs no Firestore write at all.
     */
    var publishedLeagueScoreSignature: String?
        get() = prefs.getString(KEY_PUBLISHED_LEAGUE_SIGNATURE, null)
        set(value) = prefs.edit { putString(KEY_PUBLISHED_LEAGUE_SIGNATURE, value) }

    /** Re-reads the period total; call on resume in case the month rolled over while the app sat open. */
    fun refreshPeriodXp() {
        _periodXp.value = readPeriodXp()
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

    /**
     * Erases everything that belongs to the PLAYER rather than to the phone,
     * so the next account starts from a genuinely clean slate.
     *
     * The split is the whole point. Sound/music/vibration, the notification
     * toggle, whether the tutorial has been seen and the bot-training gate
     * are properties of this device and its owner's preferences — they
     * survive. Every counter, streak, cosmetic choice and name below is
     * part of a player's progress and MUST NOT be visible under somebody
     * else's account: leaving any one of them behind is exactly how a
     * level 4 profile kept showing up on a brand-new account.
     *
     * Adding a new progress-bearing preference means adding it here too —
     * a key left out of this list is a key that leaks across accounts.
     *
     * The counters are written as explicit ZEROES rather than removed, and
     * that difference matters: [seedLifetimeScoreIfAbsent] and
     * [seedLifetimeXpFromLegacyScore] run on every launch and both key off
     * `prefs.contains(...)`, so a removed key is an invitation for them to
     * reconstruct a level from whatever local game history survived. A key
     * that is present and zero is a key those migrations leave alone —
     * there is no path back to the old number.
     *
     * Uses commit() rather than apply(): the caller wipes and then restarts
     * the process (see util.AppRestarter), and apply()'s write is
     * asynchronous — a restart racing it could come back up with some keys
     * still holding the previous account's values.
     */
    fun clearAccountScopedState() {
        prefs.edit(commit = true) { stageAccountScopedClear() }
        _lifetimeScore.value = 0
        _lifetimeXp.value = 0
        _lifetimeWordsDrawn.value = 0
        _nickname.value = ""
        _selectedAvatarFrameId.value = AvatarFrame.DEFAULT.name
        _selectedPenSkinId.value = PenSkin.DEFAULT.name
        _periodXp.value = 0
        _phraseUsageCounts.value = emptyMap()
        _emojiUsageCounts.value = emptyMap()
        _earnedLeagueRewardIds.value = emptySet()
    }

    /**
     * Stages the clear onto an editor the CALLER commits, rather than
     * committing one of its own.
     *
     * That is the whole point of it being separate. [replaceWithAccount]
     * needs the clear and the restore to reach the disk as one write; when
     * it could only get the clear by calling something that committed on its
     * own, the disk went through a state where the account was wiped and the
     * new values had not arrived yet. See [replaceWithAccount] for what that
     * cost.
     */
    private fun SharedPreferences.Editor.stageAccountScopedClear() {
        putInt(KEY_LIFETIME_SCORE, 0)
        putInt(KEY_LIFETIME_XP, 0)
        putInt(KEY_PENALTIES_APPLIED, 0)
        putInt(KEY_LIFETIME_WORDS_DRAWN, 0)
        putInt(KEY_LIFETIME_GAMES_PLAYED, 0)
        putInt(KEY_LIFETIME_PERFECT_ROUNDS, 0)
        putInt(KEY_LIFETIME_ONLINE_WINS, 0)
        putInt(KEY_BEST_STREAK, 0)
        putString(KEY_NICKNAME, "")
        // The incoming account has not named itself on this device, so
        // it should get its own Google name rather than inheriting the
        // previous player's "leave it blank" decision.
        putBoolean(KEY_NICKNAME_CHOSEN, false)
        putString(KEY_SELECTED_AVATAR_FRAME, AvatarFrame.DEFAULT.name)
        putString(KEY_SELECTED_PEN_SKIN, PenSkin.DEFAULT.name)
        // The league standing is this player's, not the phone's —
        // left behind, the new account would open the league table
        // already holding somebody else's XP for the week.
        putInt(KEY_PERIOD_XP, 0)
        remove(KEY_PERIOD_XP_PERIOD)
        // Prizes belong to the account that won them, not to the phone.
        remove(KEY_EARNED_LEAGUE_REWARDS)
        // Same for the play streak the reminder worker tracks.
        remove(KEY_LAST_PLAYED_EPOCH_DAY)
        putInt(KEY_CURRENT_STREAK, 0)
        // The Quick Match daily bonus belongs to the account, not the phone.
        remove(KEY_LAST_QUICK_MATCH_EPOCH_DAY)
        // LeagueScorePublisher skips the write when the signature it
        // last published still matches. Carried over, the new account
        // would look like it had already published — and would never
        // appear in its own friends' league table at all.
        remove(KEY_PUBLISHED_LEAGUE_SIGNATURE)
        remove(KEY_PHRASE_USAGE_COUNTS)
        remove(KEY_EMOJI_USAGE_COUNTS)
    }

    /**
     * Adopts an account's cloud backup outright, replacing whatever this
     * device held — used only when the signed-in uid itself changed, never
     * for an ordinary restore.
     *
     * Replaces rather than merges, and that distinction is the whole fix:
     * a merge assumes this device's numbers and the backup describe the
     * SAME player at two points in time, but across an account switch they
     * describe two DIFFERENT players — so a level 4 profile must not
     * survive a max() against a level 1 account it has nothing to do with.
     * Every account-scoped key is staged to zero first, so a field the
     * backup happens not to carry is left at zero rather than at the
     * previous account's value.
     *
     * ### One commit, and why this is the bug that ate an account
     *
     * This used to clear by calling [clearAccountScopedState] — which
     * commits — and then write the restored values with `prefs.edit { }`,
     * which is `apply()` and therefore ASYNCHRONOUS. Immediately afterwards
     * the caller restarts the process (util.AppRestarter →
     * `Runtime.getRuntime().exit(0)`), and `exit()` does not flush pending
     * `apply()` writes: the framework only waits for them at Activity
     * lifecycle transitions, never at an arbitrary process exit.
     *
     * So the disk got the zeroes, durably, and then the process died before
     * the level-5 profile that was supposed to replace them ever left
     * memory. The app came back up, read the zeroes, and the account was
     * gone — a signed-out-and-back-in player put at level 1. Being a race,
     * it survived every reasoned walk through the code and only ever showed
     * up on a real device.
     *
     * The fix is not a bigger `commit`: it is that there must be no moment,
     * on disk, where this account is cleared but not yet restored. Both
     * halves go into one editor and land together or not at all.
     */
    fun replaceWithAccount(
        lifetimeScore: Int,
        lifetimeXp: Int,
        lifetimeWordsDrawn: Int,
        lifetimeGamesPlayed: Int,
        lifetimePerfectRounds: Int,
        lifetimeOnlineWins: Int,
        bestStreak: Int,
        nickname: String,
        selectedAvatarFrameId: String,
        selectedPenSkinId: String,
        earnedLeagueRewardIds: Set<String>
    ) {
        val frame = selectedAvatarFrameId.ifBlank { AvatarFrame.DEFAULT.name }
        val pen = selectedPenSkinId.ifBlank { PenSkin.DEFAULT.name }
        prefs.edit(commit = true) {
            stageAccountScopedClear()
            putInt(KEY_LIFETIME_SCORE, lifetimeScore)
            putInt(KEY_LIFETIME_XP, lifetimeXp)
            putInt(KEY_LIFETIME_WORDS_DRAWN, lifetimeWordsDrawn)
            putInt(KEY_LIFETIME_GAMES_PLAYED, lifetimeGamesPlayed)
            putInt(KEY_LIFETIME_PERFECT_ROUNDS, lifetimePerfectRounds)
            putInt(KEY_LIFETIME_ONLINE_WINS, lifetimeOnlineWins)
            putInt(KEY_BEST_STREAK, bestStreak)
            putString(KEY_NICKNAME, nickname)
            // A restored account that already had a name had chosen one; an
            // account whose backup carries no name has not, and should still
            // be offered its Google one.
            putBoolean(KEY_NICKNAME_CHOSEN, nickname.isNotBlank())
            putString(KEY_SELECTED_AVATAR_FRAME, frame)
            putString(KEY_SELECTED_PEN_SKIN, pen)
            putString(KEY_EARNED_LEAGUE_REWARDS, Json.encodeToString(earnedLeagueRewardIds))
        }
        _periodXp.value = 0
        _phraseUsageCounts.value = emptyMap()
        _emojiUsageCounts.value = emptyMap()
        _earnedLeagueRewardIds.value = earnedLeagueRewardIds
        _lifetimeScore.value = lifetimeScore
        _lifetimeXp.value = lifetimeXp
        _lifetimeWordsDrawn.value = lifetimeWordsDrawn
        _nickname.value = nickname
        _selectedAvatarFrameId.value = frame
        _selectedPenSkinId.value = pen
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
        _notificationsEnabled.value = enabled
    }

    /**
     * The last day the daily reminder actually posted a notification.
     *
     * Two independent schedulers now drive that reminder — an alarm and a
     * WorkManager backstop, see NotificationScheduler — precisely because
     * either one alone can be silently dropped by the OS. That redundancy is
     * the point, and this is what keeps it from being felt: whichever fires
     * first claims the day, and the other finds it taken and does nothing.
     *
     * Device-scoped, NOT account-scoped: it describes what this phone's
     * status bar has already shown today, which has nothing to do with who
     * is signed in — so it is deliberately absent from
     * [clearAccountScopedState], where clearing it would let a sign-out
     * produce a second reminder on the same day.
     */
    var lastReminderEpochDay: Long
        get() = prefs.getLong(KEY_LAST_REMINDER_EPOCH_DAY, -1L)
        set(value) = prefs.edit { putLong(KEY_LAST_REMINDER_EPOCH_DAY, value) }

    // False until the first-run tutorial (see presentation/tutorial/) has been
    // played or skipped — decides the app's start destination on launch.
    var tutorialCompleted: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_COMPLETED, value) }

    /**
     * Whether this device has ever entered the Bot Eğitim passcode (see
     * BotTrainingGate). Remembered rather than asked every time: the gate is
     * there to keep the tile from being wandered into by players, not to
     * defend the screen from the person holding the phone — and the handful
     * of people actually training the bot would otherwise retype the code on
     * every cold start.
     *
     * The whole feature, gate included, comes out once training is done.
     */
    var botTrainingUnlocked: Boolean
        get() = prefs.getBoolean(KEY_BOT_TRAINING_UNLOCKED, false)
        set(value) = prefs.edit { putBoolean(KEY_BOT_TRAINING_UNLOCKED, value) }

    // Guards the one-time automatic permission prompt in MainActivity so it
    // only ever fires on a device's very first launch, not every cold start.
    var notificationPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, value) }

    // Guards the one-time post-first-match Google sign-in nudge and the
    // one-time post-third-match Play Store rating nudge (see
    // util/PostMatchPrompts.kt) — each flips true the moment its dialog is
    // shown, not when the player acts on it, so a dismissed prompt never
    // comes back either. Device-scoped like tutorialCompleted: what this
    // phone has already interrupted the player with has nothing to do with
    // which account is signed in.
    var signInPromptShown: Boolean
        get() = prefs.getBoolean(KEY_SIGN_IN_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_SIGN_IN_PROMPT_SHOWN, value) }

    var ratingPromptShown: Boolean
        get() = prefs.getBoolean(KEY_RATING_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_RATING_PROMPT_SHOWN, value) }

    /**
     * Pays the rating-prompt's 500 XP bonus exactly once, ever — a second
     * call (a retried dialog action, a process death replaying the tap)
     * returns false and grants nothing instead of paying out again. Separate
     * from [ratingPromptShown]: that flag only guards the DIALOG appearing,
     * this one guards the XP itself, the same split addScore/addXp keep
     * from every other reward path.
     */
    fun grantRatingBonusXpOnce(amount: Int): Boolean {
        if (prefs.getBoolean(KEY_RATING_BONUS_XP_GRANTED, false)) return false
        prefs.edit { putBoolean(KEY_RATING_BONUS_XP_GRANTED, true) }
        addXp(amount)
        return true
    }

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
        const val KEY_MUSIC = "music_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_NICKNAME = "online_nickname"
        const val KEY_SELECTED_AVATAR_FRAME = "selected_avatar_frame"
        const val KEY_SELECTED_PEN_SKIN = "selected_pen_skin"
        // Renamed from the weekly keys rather than reused: the value means a
        // month now, and an upgrading device must start the new period at zero
        // instead of inheriting a part-week total as its monthly one.
        const val KEY_PERIOD_XP = "period_xp"
        const val KEY_PERIOD_XP_PERIOD = "period_xp_period_id"
        const val KEY_PHRASE_USAGE_COUNTS = "chat_phrase_usage_counts"
        const val KEY_EMOJI_USAGE_COUNTS = "chat_emoji_usage_counts"
        const val KEY_EARNED_LEAGUE_REWARDS = "earned_league_rewards"
        const val KEY_LIFETIME_SCORE = "lifetime_score"
        const val KEY_LIFETIME_XP = "lifetime_xp"
        const val KEY_PENALTIES_APPLIED = "penalties_applied"
        const val KEY_LIFETIME_WORDS_DRAWN = "lifetime_words_drawn"
        const val KEY_LIFETIME_GAMES_PLAYED = "lifetime_games_played"
        const val KEY_LIFETIME_PERFECT_ROUNDS = "lifetime_perfect_rounds"
        const val KEY_LIFETIME_ONLINE_WINS = "lifetime_online_wins"
        const val KEY_BEST_STREAK = "best_streak"
        const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LAST_PLAYED_EPOCH_DAY = "last_played_epoch_day"
        const val KEY_LAST_QUICK_MATCH_EPOCH_DAY = "last_quick_match_epoch_day"
        const val KEY_CURRENT_STREAK = "current_streak"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        const val KEY_LAST_REMINDER_EPOCH_DAY = "last_reminder_epoch_day"
        const val KEY_SIGN_IN_PROMPT_SHOWN = "sign_in_prompt_shown"
        const val KEY_RATING_PROMPT_SHOWN = "rating_prompt_shown"
        const val KEY_RATING_BONUS_XP_GRANTED = "rating_bonus_xp_granted"
        const val KEY_NICKNAME_CHOSEN = "nickname_chosen_by_player"
        const val KEY_BOT_TRAINING_UNLOCKED = "bot_training_unlocked"
        const val KEY_PUBLISHED_LEAGUE_SIGNATURE = "published_league_score_signature"
    }
}
