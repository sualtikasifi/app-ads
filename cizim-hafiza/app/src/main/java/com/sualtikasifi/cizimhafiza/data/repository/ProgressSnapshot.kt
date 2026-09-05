package com.sualtikasifi.cizimhafiza.data.repository

import kotlinx.serialization.Serializable

/**
 * Everything that makes up a player's progress, in one value.
 *
 * Exists so the same snapshot can be written to two independent places —
 * the account's cloud backup and a local archive on this device (see
 * BackupRepositoryImpl.archiveLocally) — from one definition. Progress
 * used to live in exactly one destroyable place: a cloud document keyed by
 * a uid the player could stop being. Signing out then deleted the only
 * local copy on the strength of a cloud write nobody had verified, so a
 * single failed or skipped upload was the whole account, gone.
 *
 * Adding a field here means adding it to [BackupRepositoryImpl.buildSnapshot]
 * and to the apply path — a field this class does not carry is a field that
 * does not survive a sign-out.
 */
@Serializable
data class ProgressSnapshot(
    val lifetimeScore: Int,
    val lifetimeXp: Int,
    val lifetimeWordsDrawn: Int,
    val lifetimeGamesPlayed: Int,
    val lifetimePerfectRounds: Int,
    val lifetimeOnlineWins: Int,
    val bestStreak: Int,
    val nickname: String,
    val selectedAvatarFrameId: String,
    val selectedPenSkinId: String,
    val dailyLastCompletedEpochDay: Long,
    val dailyCurrentStreak: Int,
    val dailyBestStreak: Int,
    val unlockedAchievementIds: List<String>,
    /** One "worldId:levelIndex:stars:score" entry per cleared level. */
    val levelProgress: List<String>,
    val backedUpAt: Long
) {
    /**
     * True when this snapshot holds nothing worth keeping. Used to refuse
     * to overwrite a real backup with an empty one — the shape every
     * account-loss bug in this area ultimately took.
     */
    val isEmpty: Boolean
        get() = lifetimeXp == 0 && lifetimeScore == 0 && lifetimeGamesPlayed == 0 &&
            unlockedAchievementIds.isEmpty() && levelProgress.isEmpty()

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "lifetimeScore" to lifetimeScore,
        "lifetimeXp" to lifetimeXp,
        "lifetimeWordsDrawn" to lifetimeWordsDrawn,
        "lifetimeGamesPlayed" to lifetimeGamesPlayed,
        "lifetimePerfectRounds" to lifetimePerfectRounds,
        "lifetimeOnlineWins" to lifetimeOnlineWins,
        "bestStreak" to bestStreak,
        "nickname" to nickname,
        "selectedAvatarFrameId" to selectedAvatarFrameId,
        "selectedPenSkinId" to selectedPenSkinId,
        "dailyLastCompletedEpochDay" to dailyLastCompletedEpochDay,
        "dailyCurrentStreak" to dailyCurrentStreak,
        "dailyBestStreak" to dailyBestStreak,
        "unlockedAchievementIds" to unlockedAchievementIds,
        "levelProgress" to levelProgress,
        "backedUpAt" to backedUpAt
    )
}
