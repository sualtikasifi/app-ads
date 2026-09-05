package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.data.local.dao.DrawingResultDao
import com.sualtikasifi.cizimhafiza.data.local.dao.GameSessionDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.UnlockedAchievementEntity
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.util.DailyChallengeRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * See BackupRepository's doc for the Firestore path and why only a
 * [com.sualtikasifi.cizimhafiza.domain.repository.AuthState.Linked] uid can
 * meaningfully use this. Written as a flat field map (not a JSON blob)
 * so the backup is readable straight from the Firebase console, matching
 * every other document in this app.
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val achievementDao: AchievementDao,
    private val levelProgressDao: LevelProgressDao,
    private val gameSessionDao: GameSessionDao,
    private val drawingResultDao: DrawingResultDao
) : BackupRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Tolerant on read so an archive written by an older build still loads rather than being discarded. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * How many account transitions are currently open — see
     * [beginAccountTransition] for what that span covers and why a backup
     * fired inside it destroys data. Counted rather than a flag so the
     * repository's own internal bracketing ([switchToAccount],
     * [clearLocalProgress]) nests safely inside the caller's wider one
     * without the inner end lifting the guard early.
     */
    private val accountTransitionDepth = AtomicInteger(0)

    override fun beginAccountTransition() {
        accountTransitionDepth.incrementAndGet()
    }

    override fun endAccountTransition() {
        accountTransitionDepth.updateAndGet { current -> if (current > 0) current - 1 else 0 }
    }

    private inline fun <T> withAccountTransition(block: () -> T): T {
        beginAccountTransition()
        try {
            return block()
        } finally {
            endAccountTransition()
        }
    }

    private val _lastBackupAtMillis = MutableStateFlow(
        prefs.getLong(KEY_LAST_BACKUP_AT, -1L).takeIf { it >= 0 }
    )
    override val lastBackupAtMillis: StateFlow<Long?> = _lastBackupAtMillis.asStateFlow()

    private fun backupDoc(uid: String) = firestore.collection("users").document(uid)
        .collection("backup").document("state")

    /**
     * Collects this device's whole progress into one value — see
     * [ProgressSnapshot] for why it exists as a value rather than being
     * assembled inline at each destination.
     */
    private suspend fun buildSnapshot(): ProgressSnapshot {
        val daily = dailyChallengeRepository.state.value
        return ProgressSnapshot(
            lifetimeScore = settingsRepository.lifetimeScore.value,
            lifetimeXp = settingsRepository.lifetimeXp.value,
            lifetimeWordsDrawn = settingsRepository.lifetimeWordsDrawn.value,
            lifetimeGamesPlayed = settingsRepository.lifetimeGamesPlayed,
            lifetimePerfectRounds = settingsRepository.lifetimePerfectRounds,
            lifetimeOnlineWins = settingsRepository.lifetimeOnlineWins,
            bestStreak = settingsRepository.bestStreak,
            nickname = settingsRepository.nickname.value,
            selectedAvatarFrameId = settingsRepository.selectedAvatarFrameId.value,
            selectedPenSkinId = settingsRepository.selectedPenSkinId.value,
            // The daily challenge's OWN streak (see DailyChallengeRepository's
            // class doc on why it's tracked separately from currentStreak
            // above) — taken from its stored value, not the exposed state's
            // (which reads back 0 once a streak has lapsed by more than a
            // day; the stored number is what repairStreak works from).
            dailyLastCompletedEpochDay = daily.lastCompletedEpochDay,
            dailyCurrentStreak = daily.currentStreak,
            dailyBestStreak = daily.bestStreak,
            unlockedAchievementIds = achievementDao.getUnlockedIds(),
            // The level map's stars. Everything else here is a lifetime
            // counter that a fresh install can only gain, but the 90-level
            // climb was the one thing a player rebuilt from zero on a new
            // phone — the backup restored their XP and achievements and
            // still handed them a locked map. Stored as one "w:l:stars:score"
            // string per cleared level: compact enough for a single
            // document, and still readable in the Firebase console.
            levelProgress = levelProgressDao.getAll().map {
                "${it.worldId}:${it.levelIndex}:${it.bestStars}:${it.bestScore}"
            },
            backedUpAt = System.currentTimeMillis()
        )
    }

    override suspend fun backupNow(): Result<Unit> = runCatching {
        // A FAILURE, not a quiet success. This used to return success
        // without writing anything, on the reasoning that the caller
        // (AutoBackupPublisher) had nothing to fix — but signOut() also
        // calls this, and treats success as "the account's progress is
        // safely in the cloud, the device can now be wiped". A suppressed
        // backup reported as success is therefore a direct instruction to
        // delete unsaved progress. Callers that genuinely do not care
        // ignore the result; the one that does now gets the truth.
        check(accountTransitionDepth.get() == 0) { "Backup suppressed during an account transition" }
        val uid = authRepository.ensureSignedIn()
        val snapshot = buildSnapshot()
        // Bounded, because this Task resolves on the SERVER's acknowledgement
        // and offline that never comes — an unbounded await here left the
        // sign-out that waits on it spinning forever with no way out. The
        // write is not lost on timeout: Firestore's local persistence has
        // already durably queued it (see FirebaseModule) and sends it when
        // the connection returns, so timing out means "not confirmed yet",
        // not "not saved".
        withTimeout(BACKUP_TIMEOUT_MS) { backupDoc(uid).set(snapshot.toFirestoreMap()).await() }
        // The device's own copy, kept in step with every cloud write. Costs
        // one small preference write and is what makes progress survive a
        // sign-out whose upload never actually landed.
        archiveLocally(uid, snapshot)
        prefs.edit { putLong(KEY_LAST_BACKUP_AT, snapshot.backedUpAt) }
        _lastBackupAtMillis.value = snapshot.backedUpAt
    }

    override suspend fun switchToAccount(): Result<Boolean> = runCatching {
        withAccountTransition { adoptSignedInAccount() }
    }

    /**
     * The device's own copy of an account's progress, kept alongside the
     * cloud one and keyed by uid.
     *
     * This is the safety net the whole area was missing. A sign-out used to
     * delete the only local copy on the strength of one unverified cloud
     * write; if that write was skipped, throttled, offline or simply failed,
     * the account was gone with nothing left anywhere to restore from. Now
     * the phone keeps its own copy, so recovering a signed-out account
     * needs only the same device — no network, no working cloud write, no
     * trust in a single point of failure.
     *
     * Deliberately never cleared by [wipeAccountScopedState]: an archive
     * that a sign-out erases is not an archive.
     */
    private fun archiveLocally(uid: String, snapshot: ProgressSnapshot) {
        runCatching {
            val encoded = json.encodeToString(snapshot)
            prefs.edit(commit = true) {
                putString(archiveKey(uid), encoded)
                // Also filed under the Google address, because a uid is not
                // actually a stable way to find your own progress again: an
                // older build's "Bağlantıyı Kes" could detach an account
                // from its uid, after which signing back in produced a
                // BRAND NEW uid and the old one — holding everything —
                // became unreachable forever. An email-keyed copy survives
                // exactly that: same phone, same Google address, progress
                // found regardless of how many uids happened in between.
                signedInEmail()?.let { putString(emailArchiveKey(it), encoded) }
            }
        }.onFailure { Log.w(TAG, "Local archive write failed for $uid", it) }
    }

    /**
     * This account's archived progress: the copy filed under its uid, or
     * failing that the one filed under its Google address (see
     * [archiveLocally] for why the second key exists).
     */
    private fun readArchive(uid: String): ProgressSnapshot? =
        decodeArchive(prefs.getString(archiveKey(uid), null))
            ?: decodeArchive(signedInEmail()?.let { prefs.getString(emailArchiveKey(it), null) })

    private fun decodeArchive(raw: String?): ProgressSnapshot? {
        if (raw == null) return null
        return runCatching { json.decodeFromString<ProgressSnapshot>(raw) }
            .onFailure { Log.w(TAG, "Local archive unreadable", it) }
            .getOrNull()
    }

    private fun signedInEmail(): String? =
        (authRepository.authState.value as? AuthState.Linked)?.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun archiveKey(uid: String) = "$KEY_ARCHIVE_PREFIX$uid"

    private fun emailArchiveKey(email: String) = "$KEY_ARCHIVE_EMAIL_PREFIX$email"

    override suspend fun archiveForSignOut(): Result<Unit> = runCatching {
        val uid = authRepository.ensureSignedIn()
        val snapshot = buildSnapshot()
        if (snapshot.isEmpty) return@runCatching
        archiveLocally(uid, snapshot)
        // Read back rather than trusting the write: this archive is the
        // only thing standing between the wipe that follows and a lost
        // account, so "probably written" is not good enough.
        val readBack = readArchive(uid)
        check(readBack != null && readBack.lifetimeXp == snapshot.lifetimeXp) {
            "Refusing to sign out: local archive could not be written"
        }
    }

    override suspend fun clearLocalProgress(): Result<Unit> = runCatching {
        withAccountTransition {
            wipeAccountScopedState()
            // The outgoing account's backup timestamp is theirs, not the next
            // player's — left behind, a fresh account's Hesap screen would
            // claim to have been backed up before it ever existed.
            prefs.edit { remove(KEY_LAST_BACKUP_AT) }
            _lastBackupAtMillis.value = null
        }
    }

    /**
     * Everything on this device that belongs to whoever was signed in —
     * see SettingsRepository.clearAccountScopedState for the preference
     * half and why sound/language/tutorial are deliberately not in it.
     *
     * The word pool (`words`, and the review tables that annotate it) is
     * emphatically NOT cleared: it is a downloaded catalog shared by every
     * account on the phone, and wiping it would cost a full resync for no
     * reason at all.
     *
     * The preferences go FIRST and every step stands on its own. Level, XP
     * and name all live in preferences, so those are what a player sees and
     * what makes a sign-out look like it worked; running them behind four
     * database deletes meant any one of those throwing left the visible
     * profile completely untouched — a wipe that silently did nothing at
     * all. Ordering them first and isolating each failure means the worst
     * case is now some stale rows in a table, never a level that refuses to
     * reset.
     *
     * Throws if the preference wipe itself failed, so the caller can tell
     * the player the truth rather than restarting into the old profile.
     */
    private suspend fun wipeAccountScopedState() {
        settingsRepository.clearAccountScopedState()
        runCatching { dailyChallengeRepository.clearAccountScopedState() }
            .onFailure { Log.w(TAG, "wipe: daily challenge state survived", it) }
        runCatching { levelProgressDao.deleteAll() }
            .onFailure { Log.w(TAG, "wipe: level progress survived", it) }
        runCatching { achievementDao.deleteAll() }
            .onFailure { Log.w(TAG, "wipe: achievements survived", it) }
        runCatching { gameSessionDao.deleteAll() }
            .onFailure { Log.w(TAG, "wipe: game sessions survived", it) }
        runCatching { drawingResultDao.deleteAll() }
            .onFailure { Log.w(TAG, "wipe: drawing results survived", it) }

        // The one invariant worth failing loudly over: if this is still not
        // zero the player is about to be restarted straight back into the
        // profile they just signed out of.
        check(settingsRepository.lifetimeXp.value == 0) {
            "Account-scoped preferences did not reset (lifetimeXp=${settingsRepository.lifetimeXp.value})"
        }
    }

    /**
     * Makes local progress be this account's progress, taking whichever of
     * the two independent copies actually holds more.
     *
     * Two sources, because either one alone has failed in practice: the
     * account's cloud backup, and this device's own archive of it (see
     * [archiveLocally]). A cloud read that comes back empty is not proof
     * the account is new — it is equally the signature of a write that
     * never landed — so an archive holding real progress wins over an
     * absent or emptier cloud copy rather than being ignored.
     */
    private suspend fun adoptSignedInAccount(): Boolean {
        val uid = authRepository.ensureSignedIn()
        val remote = runCatching { backupDoc(uid).get().await().toSnapshot() }
            .onFailure { Log.w(TAG, "Cloud backup unreadable for $uid", it) }
            .getOrNull()
        val archived = readArchive(uid)
        val restored = pickRicher(remote, archived)

        // Wiped unconditionally before either outcome below: neither a fresh
        // account nor a different account's own backup should ever see the
        // PREVIOUS account's rows merged in (LevelProgressDao.upsert and
        // AchievementDao.insert both keep-the-better, which is exactly wrong
        // across an identity change).
        wipeAccountScopedState()

        if (restored == null || restored.isEmpty) {
            // Nothing anywhere, and nothing left over either — this account
            // genuinely starts at level 1, which is the whole point.
            prefs.edit { remove(KEY_LAST_BACKUP_AT) }
            _lastBackupAtMillis.value = null
            return false
        }

        apply(restored)
        // Re-archived under this uid so the copy that saved the account is
        // itself preserved for next time — including when it came from the
        // cloud onto a phone that had never seen this account before.
        archiveLocally(uid, restored)
        if (restored.backedUpAt > 0L) {
            prefs.edit { putLong(KEY_LAST_BACKUP_AT, restored.backedUpAt) }
            _lastBackupAtMillis.value = restored.backedUpAt
        }
        return true
    }

    /**
     * The copy with more progress in it, not simply the newer one. A
     * freshly-written empty backup is newer than the real one it replaced,
     * so trusting timestamps alone is how progress gets overwritten by
     * nothing; lifetime XP only ever grows, which makes it the honest
     * tie-break.
     */
    private fun pickRicher(remote: ProgressSnapshot?, archived: ProgressSnapshot?): ProgressSnapshot? = when {
        remote == null -> archived
        archived == null -> remote
        archived.lifetimeXp > remote.lifetimeXp -> archived
        remote.lifetimeXp > archived.lifetimeXp -> remote
        else -> if (archived.backedUpAt > remote.backedUpAt) archived else remote
    }

    private suspend fun apply(snapshot: ProgressSnapshot) {
        settingsRepository.replaceWithAccount(
            lifetimeScore = snapshot.lifetimeScore,
            lifetimeXp = snapshot.lifetimeXp,
            lifetimeWordsDrawn = snapshot.lifetimeWordsDrawn,
            lifetimeGamesPlayed = snapshot.lifetimeGamesPlayed,
            lifetimePerfectRounds = snapshot.lifetimePerfectRounds,
            lifetimeOnlineWins = snapshot.lifetimeOnlineWins,
            bestStreak = snapshot.bestStreak,
            nickname = snapshot.nickname,
            selectedAvatarFrameId = snapshot.selectedAvatarFrameId,
            selectedPenSkinId = snapshot.selectedPenSkinId
        )
        dailyChallengeRepository.replaceWithAccount(
            lastCompletedEpochDay = snapshot.dailyLastCompletedEpochDay,
            currentStreak = snapshot.dailyCurrentStreak,
            bestStreak = snapshot.dailyBestStreak
        )
        snapshot.levelProgress.forEach { row ->
            val parts = row.split(":")
            if (parts.size != 4) return@forEach
            val worldId = parts[0].toIntOrNull() ?: return@forEach
            val levelIndex = parts[1].toIntOrNull() ?: return@forEach
            val stars = parts[2].toIntOrNull() ?: return@forEach
            val score = parts[3].toIntOrNull() ?: return@forEach
            levelProgressDao.upsert(
                LevelProgressEntity(
                    worldId = worldId,
                    levelIndex = levelIndex,
                    bestStars = stars,
                    bestScore = score,
                    lastPlayedEpochMillis = System.currentTimeMillis()
                )
            )
        }
        // seen=true on purpose: these were earned on another device or in an
        // earlier session, so they must never light up this device's "new
        // achievement" badge.
        val now = System.currentTimeMillis()
        snapshot.unlockedAchievementIds.forEach { id ->
            achievementDao.insert(UnlockedAchievementEntity(id = id, unlockedAtMillis = now, seen = true))
        }
    }

    /** Null for a document that does not exist — see adoptSignedInAccount for why that is not the same as "new account". */
    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toSnapshot(): ProgressSnapshot? {
        if (!exists()) return null
        return ProgressSnapshot(
            lifetimeScore = (getLong("lifetimeScore") ?: 0L).toInt(),
            lifetimeXp = (getLong("lifetimeXp") ?: 0L).toInt(),
            lifetimeWordsDrawn = (getLong("lifetimeWordsDrawn") ?: 0L).toInt(),
            lifetimeGamesPlayed = (getLong("lifetimeGamesPlayed") ?: 0L).toInt(),
            lifetimePerfectRounds = (getLong("lifetimePerfectRounds") ?: 0L).toInt(),
            lifetimeOnlineWins = (getLong("lifetimeOnlineWins") ?: 0L).toInt(),
            bestStreak = (getLong("bestStreak") ?: 0L).toInt(),
            nickname = getString("nickname").orEmpty(),
            selectedAvatarFrameId = getString("selectedAvatarFrameId").orEmpty(),
            selectedPenSkinId = getString("selectedPenSkinId").orEmpty(),
            dailyLastCompletedEpochDay = getLong("dailyLastCompletedEpochDay") ?: -1L,
            dailyCurrentStreak = (getLong("dailyCurrentStreak") ?: 0L).toInt(),
            dailyBestStreak = (getLong("dailyBestStreak") ?: 0L).toInt(),
            unlockedAchievementIds = get("unlockedAchievementIds") as? List<String> ?: emptyList(),
            levelProgress = get("levelProgress") as? List<String> ?: emptyList(),
            backedUpAt = getLong("backedUpAt") ?: 0L
        )
    }

    private companion object {
        const val TAG = "BackupRepository"

        /** Long enough for a slow connection to confirm, short enough that a sign-out never appears to hang. */
        const val BACKUP_TIMEOUT_MS = 15_000L
        const val PREFS_NAME = "karalak_backup"
        const val KEY_LAST_BACKUP_AT = "last_backup_at_millis"

        /**
         * Per-uid local copy of an account's progress. Survives a sign-out
         * on purpose (see archiveLocally) — it is the copy that makes the
         * wipe recoverable, so a wipe must never be able to reach it.
         */
        const val KEY_ARCHIVE_PREFIX = "progress_archive_"

        /** The same archive, findable by Google address when the uid is no longer reachable. */
        const val KEY_ARCHIVE_EMAIL_PREFIX = "progress_archive_email_"
    }
}
