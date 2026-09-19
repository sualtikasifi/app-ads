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
    /**
     * League prizes this account has won — see
     * SettingsRepository.earnedLeagueRewardIds.
     *
     * Carried here because it is the only record they were won. Nothing else
     * in the app can re-derive a prize: it is not a function of XP, level or
     * anything else a fresh install could recompute.
     */
    val earnedLeagueRewardIds: List<String>,
    /** One "worldId:levelIndex:stars:score" entry per cleared level. */
    val levelProgress: List<String>,
    /**
     * How many moderation penalties this device has applied — see
     * PenaltyRepository.
     *
     * Carried in the snapshot for one reason: XP is otherwise only ever
     * allowed to go UP. Both the local archive and the cloud restore refuse a
     * lower figure on purpose, because every account-loss bug in this area
     * looked like a smaller number arriving over a bigger one. A penalty is
     * the single legitimate exception, and this counter is how the guards
     * tell the two apart — a snapshot that has applied MORE penalties is
     * newer even when its XP is smaller.
     */
    val penaltiesApplied: Int = 0,
    /**
     * Chest gold (see SettingsRepository.goldBalance) — the one part of the
     * chest economy that travels with the account. The chests themselves
     * (SettingsRepository.chestSlots) deliberately do NOT: an in-progress
     * unlock countdown is this device's business, not something a restore
     * should teleport to a different phone.
     */
    val goldBalance: Int = 0,
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
        "penaltiesApplied" to penaltiesApplied,
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
        "earnedLeagueRewardIds" to earnedLeagueRewardIds,
        "levelProgress" to levelProgress,
        "goldBalance" to goldBalance,
        "backedUpAt" to backedUpAt
    )

    companion object {

        /**
         * The inverse of [toFirestoreMap].
         *
         * Takes a plain map rather than a Firestore DocumentSnapshot so the
         * round trip can actually be tested. The pair of functions is the
         * narrowest point in the whole backup path — a field added to one
         * side and forgotten on the other silently stops surviving a
         * sign-out, with nothing failing and nothing to see until somebody
         * signs back in and finds it gone.
         */
        fun fromFirestoreMap(data: Map<String, Any?>): ProgressSnapshot = ProgressSnapshot(
            lifetimeScore = data.int("lifetimeScore"),
            lifetimeXp = data.int("lifetimeXp"),
            lifetimeWordsDrawn = data.int("lifetimeWordsDrawn"),
            lifetimeGamesPlayed = data.int("lifetimeGamesPlayed"),
            lifetimePerfectRounds = data.int("lifetimePerfectRounds"),
            lifetimeOnlineWins = data.int("lifetimeOnlineWins"),
            bestStreak = data.int("bestStreak"),
            nickname = data.str("nickname"),
            selectedAvatarFrameId = data.str("selectedAvatarFrameId"),
            selectedPenSkinId = data.str("selectedPenSkinId"),
            // -1 rather than 0: epoch day 0 is a real date (1 Jan 1970), so
            // a missing value has to be a day that cannot be mistaken for
            // one somebody played on.
            dailyLastCompletedEpochDay = data.long("dailyLastCompletedEpochDay", absent = -1L),
            dailyCurrentStreak = data.int("dailyCurrentStreak"),
            dailyBestStreak = data.int("dailyBestStreak"),
            unlockedAchievementIds = data.strings("unlockedAchievementIds"),
            earnedLeagueRewardIds = data.strings("earnedLeagueRewardIds"),
            levelProgress = data.strings("levelProgress"),
            goldBalance = data.int("goldBalance"),
            backedUpAt = data.long("backedUpAt")
        )

        /**
         * The copy with more progress in it, not simply the newer one.
         *
         * A cloud read that comes back empty does not mean "this account is
         * new" — it can equally mean the write never arrived. Trusting the
         * timestamp would then let an empty record overwrite real progress,
         * which is the shape every account-loss bug here has taken. Only
         * when both hold the same XP does recency decide.
         */
        fun richer(remote: ProgressSnapshot?, archived: ProgressSnapshot?): ProgressSnapshot? {
            val winner = pickRicher(remote, archived) ?: return null
            // League prizes are UNIONED across both copies rather than taken
            // from the winner alone. Everything else here is a number that
            // one side simply has more of; a prize is a fact, and two
            // devices can each hold a week the other never saw. Picking one
            // copy wholesale would quietly un-win the other's.
            val allRewards = (
                remote?.earnedLeagueRewardIds.orEmpty() + archived?.earnedLeagueRewardIds.orEmpty()
                ).distinct()
            return if (allRewards.size == winner.earnedLeagueRewardIds.size) winner
            else winner.copy(earnedLeagueRewardIds = allRewards)
        }

        private fun pickRicher(remote: ProgressSnapshot?, archived: ProgressSnapshot?): ProgressSnapshot? = when {
            remote == null -> archived
            archived == null -> remote
            // Penalties first, and deliberately ahead of XP: the whole point
            // of a penalty is that the smaller number is the correct one, so
            // asking "which has more XP" would hand the cheated total back
            // every time the account was restored.
            archived.penaltiesApplied > remote.penaltiesApplied -> archived
            remote.penaltiesApplied > archived.penaltiesApplied -> remote
            archived.lifetimeXp > remote.lifetimeXp -> archived
            remote.lifetimeXp > archived.lifetimeXp -> remote
            else -> if (archived.backedUpAt > remote.backedUpAt) archived else remote
        }

        private fun Map<String, Any?>.long(key: String, absent: Long = 0L): Long =
            (this[key] as? Number)?.toLong() ?: absent

        private fun Map<String, Any?>.int(key: String): Int = long(key).toInt()

        private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""

        /**
         * filterIsInstance rather than an unchecked cast to List<String>: the
         * cast succeeds on ANY list and only throws later, deep in the apply
         * path, where the failure would look like a corrupt account rather
         * than a corrupt field.
         */
        private fun Map<String, Any?>.strings(key: String): List<String> =
            (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
    }
}
