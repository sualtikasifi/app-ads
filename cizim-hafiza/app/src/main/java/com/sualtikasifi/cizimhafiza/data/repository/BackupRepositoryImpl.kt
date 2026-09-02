package com.sualtikasifi.cizimhafiza.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.data.local.dao.LevelProgressDao
import com.sualtikasifi.cizimhafiza.data.local.entity.LevelProgressEntity
import com.sualtikasifi.cizimhafiza.data.local.entity.UnlockedAchievementEntity
import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.BackupRepository
import com.sualtikasifi.cizimhafiza.util.DailyChallengeRepository
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
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
    private val levelProgressDao: LevelProgressDao
) : BackupRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastBackupAtMillis = MutableStateFlow(
        prefs.getLong(KEY_LAST_BACKUP_AT, -1L).takeIf { it >= 0 }
    )
    override val lastBackupAtMillis: StateFlow<Long?> = _lastBackupAtMillis.asStateFlow()

    private fun backupDoc(uid: String) = firestore.collection("users").document(uid)
        .collection("backup").document("state")

    override suspend fun backupNow(): Result<Unit> = runCatching {
        val uid = authRepository.ensureSignedIn()
        val daily = dailyChallengeRepository.state.value
        val payload = mapOf(
            "lifetimeScore" to settingsRepository.lifetimeScore.value,
            "lifetimeXp" to settingsRepository.lifetimeXp.value,
            "lifetimeWordsDrawn" to settingsRepository.lifetimeWordsDrawn.value,
            "lifetimeGamesPlayed" to settingsRepository.lifetimeGamesPlayed,
            "lifetimePerfectRounds" to settingsRepository.lifetimePerfectRounds,
            "lifetimeOnlineWins" to settingsRepository.lifetimeOnlineWins,
            "bestStreak" to settingsRepository.bestStreak,
            "nickname" to settingsRepository.nickname.value,
            "selectedAvatarFrameId" to settingsRepository.selectedAvatarFrameId.value,
            "selectedPenSkinId" to settingsRepository.selectedPenSkinId.value,
            // The daily challenge's OWN streak (see DailyChallengeRepository's
            // class doc on why it's tracked separately from currentStreak
            // above) — restored from its stored value, not the exposed
            // state's (which reads back 0 once a streak has lapsed by more
            // than a day; the stored number is what repairStreak works from).
            "dailyLastCompletedEpochDay" to daily.lastCompletedEpochDay,
            "dailyCurrentStreak" to daily.currentStreak,
            "dailyBestStreak" to daily.bestStreak,
            "dailyFreezesRemaining" to daily.freezesRemaining,
            "unlockedAchievementIds" to achievementDao.getUnlockedIds(),
            // The level map's stars. Everything else here is a lifetime
            // counter that a fresh install can only gain, but the 90-level
            // climb was the one thing a player rebuilt from zero on a new
            // phone — the backup restored their XP and achievements and
            // still handed them a locked map. Stored as one "w:l:stars:score"
            // string per cleared level: compact enough for a single
            // document, and still readable in the Firebase console.
            "levelProgress" to levelProgressDao.getAll().map {
                "${it.worldId}:${it.levelIndex}:${it.bestStars}:${it.bestScore}"
            },
            "backedUpAt" to System.currentTimeMillis()
        )
        backupDoc(uid).set(payload).await()
        val now = System.currentTimeMillis()
        prefs.edit { putLong(KEY_LAST_BACKUP_AT, now) }
        _lastBackupAtMillis.value = now
    }

    override suspend fun hasRemoteBackup(): Boolean {
        val uid = authRepository.ensureSignedIn()
        return runCatching { backupDoc(uid).get().await().exists() }.getOrDefault(false)
    }

    override suspend fun restoreLatest(): Result<Boolean> = runCatching {
        val uid = authRepository.ensureSignedIn()
        val snapshot = backupDoc(uid).get().await()
        if (!snapshot.exists()) return@runCatching false

        settingsRepository.restoreIfBetter(
            lifetimeScore = (snapshot.getLong("lifetimeScore") ?: 0L).toInt(),
            lifetimeXp = (snapshot.getLong("lifetimeXp") ?: 0L).toInt(),
            lifetimeWordsDrawn = (snapshot.getLong("lifetimeWordsDrawn") ?: 0L).toInt(),
            lifetimeGamesPlayed = (snapshot.getLong("lifetimeGamesPlayed") ?: 0L).toInt(),
            lifetimePerfectRounds = (snapshot.getLong("lifetimePerfectRounds") ?: 0L).toInt(),
            lifetimeOnlineWins = (snapshot.getLong("lifetimeOnlineWins") ?: 0L).toInt(),
            bestStreak = (snapshot.getLong("bestStreak") ?: 0L).toInt(),
            nickname = snapshot.getString("nickname").orEmpty(),
            selectedAvatarFrameId = snapshot.getString("selectedAvatarFrameId").orEmpty(),
            selectedPenSkinId = snapshot.getString("selectedPenSkinId").orEmpty()
        )

        dailyChallengeRepository.restoreIfBetter(
            lastCompletedEpochDay = snapshot.getLong("dailyLastCompletedEpochDay") ?: -1L,
            currentStreak = (snapshot.getLong("dailyCurrentStreak") ?: 0L).toInt(),
            bestStreak = (snapshot.getLong("dailyBestStreak") ?: 0L).toInt(),
            freezesRemaining = (snapshot.getLong("dailyFreezesRemaining") ?: 0L).toInt()
        )

        // Best-of merge, never a blind overwrite: a device that has played
        // further than the backup keeps its own stars (see
        // LevelProgressDao.upsert's max() semantics via recordLevelResult —
        // done explicitly here because restore writes the row directly).
        @Suppress("UNCHECKED_CAST")
        val levelRows = snapshot.get("levelProgress") as? List<String> ?: emptyList()
        levelRows.forEach { row ->
            val parts = row.split(":")
            if (parts.size != 4) return@forEach
            val worldId = parts[0].toIntOrNull() ?: return@forEach
            val levelIndex = parts[1].toIntOrNull() ?: return@forEach
            val stars = parts[2].toIntOrNull() ?: return@forEach
            val score = parts[3].toIntOrNull() ?: return@forEach
            val existing = levelProgressDao.getOne(worldId, levelIndex)
            if (existing != null && existing.bestStars >= stars && existing.bestScore >= score) return@forEach
            levelProgressDao.upsert(
                LevelProgressEntity(
                    worldId = worldId,
                    levelIndex = levelIndex,
                    bestStars = maxOf(stars, existing?.bestStars ?: 0),
                    bestScore = maxOf(score, existing?.bestScore ?: 0),
                    lastPlayedEpochMillis = existing?.lastPlayedEpochMillis ?: System.currentTimeMillis()
                )
            )
        }

        // IGNORE-on-conflict (see AchievementDao.insert): an id already
        // unlocked locally keeps its original unlockedAtMillis/seen state
        // untouched. seen=true here on purpose — these were earned on
        // another device, not just now, so they must never trigger this
        // device's "new achievement" badge.
        @Suppress("UNCHECKED_CAST")
        val achievementIds = snapshot.get("unlockedAchievementIds") as? List<String> ?: emptyList()
        val now = System.currentTimeMillis()
        achievementIds.forEach { id ->
            achievementDao.insert(UnlockedAchievementEntity(id = id, unlockedAtMillis = now, seen = true))
        }

        true
    }

    private companion object {
        const val PREFS_NAME = "karalak_backup"
        const val KEY_LAST_BACKUP_AT = "last_backup_at_millis"
    }
}
