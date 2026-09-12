package com.sualtikasifi.cizimhafiza.data.repository

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup path is the one place in this app that has actually destroyed
 * a player's account, and every way it did so was silent: a field that
 * stopped being carried, an empty record allowed to win, a value read back
 * as something other than what was written. None of it fails loudly, and
 * none of it is visible until somebody signs in and finds four levels gone.
 *
 * These tests cover the two pure halves of that path — the serialisation
 * round trip and the rule that decides which copy survives.
 */
class ProgressSnapshotTest {

    private val full = ProgressSnapshot(
        lifetimeScore = 4_210,
        lifetimeXp = 18_640,
        lifetimeWordsDrawn = 902,
        lifetimeGamesPlayed = 143,
        lifetimePerfectRounds = 11,
        lifetimeOnlineWins = 27,
        bestStreak = 9,
        nickname = "Zeynep",
        selectedAvatarFrameId = "BRONZE",
        selectedPenSkinId = "charcoal",
        dailyLastCompletedEpochDay = 20_340L,
        dailyCurrentStreak = 5,
        dailyBestStreak = 12,
        unlockedAchievementIds = listOf("first_game", "words_100", "streak_7"),
        earnedLeagueRewardIds = listOf("PEN:LEAGUE_AURORA"),
        levelProgress = listOf("1:0:3:60", "1:1:2:45", "2:0:3:58"),
        backedUpAt = 1_757_000_000_000L
    )

    @Test
    fun `a snapshot survives the cloud round trip unchanged`() {
        // The test that actually earns its place: add a field to
        // toFirestoreMap and forget fromFirestoreMap (or the reverse) and
        // this fails here, rather than silently dropping that field from
        // every account that signs out from now on.
        assertEquals(full, ProgressSnapshot.fromFirestoreMap(full.toFirestoreMap()))
    }

    @Test
    fun `a snapshot survives the local archive round trip unchanged`() {
        // The archive is written as JSON with the same tolerant reader the
        // repository uses, and it is the copy that has to survive the wipe.
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(full, json.decodeFromString<ProgressSnapshot>(json.encodeToString(full)))
    }

    @Test
    fun `numbers written by Firestore as Long or Double both read back`() {
        // Firestore hands numbers back as Long, but a document touched by
        // the console or an older client can carry a Double. The old reader
        // used getLong, which coerced; a plain cast would have returned 0
        // for every one of them — an account silently reset to level 1.
        val restored = ProgressSnapshot.fromFirestoreMap(
            full.toFirestoreMap() + mapOf(
                "lifetimeXp" to 18_640.0,
                "lifetimeScore" to 4_210L
            )
        )
        assertEquals(18_640, restored.lifetimeXp)
        assertEquals(4_210, restored.lifetimeScore)
    }

    @Test
    fun `an empty document reads back as an empty snapshot rather than failing`() {
        val restored = ProgressSnapshot.fromFirestoreMap(emptyMap())
        assertTrue(restored.isEmpty)
        assertEquals(0, restored.lifetimeXp)
        assertEquals("", restored.nickname)
        assertEquals(emptyList<String>(), restored.levelProgress)
        // Epoch day 0 is 1 Jan 1970 — a real date. "Never" has to be a value
        // no daily challenge could ever have been completed on.
        assertEquals(-1L, restored.dailyLastCompletedEpochDay)
    }

    @Test
    fun `a list holding something other than strings does not poison the snapshot`() {
        // An unchecked cast to List<String> succeeds on any list and only
        // throws later, while applying the account — where it looks like
        // corruption rather than one bad field.
        val restored = ProgressSnapshot.fromFirestoreMap(
            full.toFirestoreMap() + mapOf("unlockedAchievementIds" to listOf("first_game", 42, null))
        )
        assertEquals(listOf("first_game"), restored.unlockedAchievementIds)
    }

    @Test
    fun `progress alone is not enough to call a snapshot non-empty`() {
        assertTrue(full.copy(lifetimeXp = 0, lifetimeScore = 0, lifetimeGamesPlayed = 0,
            unlockedAchievementIds = emptyList(), levelProgress = emptyList()).isEmpty)
        // Any single one of them is enough to mean "there is something here
        // worth refusing to overwrite".
        assertFalse(full.copy(lifetimeXp = 1).isEmpty)
        assertFalse(full.copy(levelProgress = listOf("1:0:1:10")).isEmpty)
    }

    // --- which copy survives ---

    private val cloud = full.copy(lifetimeXp = 5_000, backedUpAt = 2_000L)
    private val local = full.copy(lifetimeXp = 9_000, backedUpAt = 1_000L)

    @Test
    fun `the richer copy wins even when it is the older one`() {
        // This is the whole rule. The local archive is older but holds more
        // progress, which means the cloud write that "replaced" it never
        // carried the difference — taking the newer one here is precisely
        // how an account gets overwritten by less than it had.
        assertSame(local, ProgressSnapshot.richer(remote = cloud, archived = local))
    }

    @Test
    fun `an empty cloud read never overwrites real local progress`() {
        // A cloud document that comes back empty does not mean the account
        // is new — it equally means the write never arrived.
        val emptyCloud = ProgressSnapshot.fromFirestoreMap(emptyMap()).copy(backedUpAt = 9_999L)
        assertSame(local, ProgressSnapshot.richer(remote = emptyCloud, archived = local))
    }

    @Test
    fun `recency decides only when both hold the same progress`() {
        val older = full.copy(lifetimeXp = 7_000, backedUpAt = 1_000L)
        val newer = full.copy(lifetimeXp = 7_000, backedUpAt = 2_000L)
        assertSame(newer, ProgressSnapshot.richer(remote = older, archived = newer))
        assertSame(newer, ProgressSnapshot.richer(remote = newer, archived = older))
    }

    /**
     * Prizes are the one field where the loser's copy still matters. Two
     * devices can each have collected a week the other never saw, and
     * picking one snapshot wholesale — which is right for every counter
     * here — would silently take a won prize away.
     */
    @Test
    fun `league prizes from both copies survive the merge`() {
        val remote = full.copy(
            lifetimeXp = full.lifetimeXp + 1,
            earnedLeagueRewardIds = listOf("PEN:LEAGUE_EMBER")
        )
        val archived = full.copy(earnedLeagueRewardIds = listOf("PEN:LEAGUE_FROST"))

        val merged = ProgressSnapshot.richer(remote, archived)!!

        // The richer copy still wins everything else…
        assertEquals(remote.lifetimeXp, merged.lifetimeXp)
        // …but neither device loses what it won.
        assertEquals(
            setOf("PEN:LEAGUE_EMBER", "PEN:LEAGUE_FROST"),
            merged.earnedLeagueRewardIds.toSet()
        )
    }

    @Test
    fun `merging identical prize lists does not duplicate them`() {
        val merged = ProgressSnapshot.richer(full, full.copy(lifetimeXp = 1))!!
        assertEquals(full.earnedLeagueRewardIds, merged.earnedLeagueRewardIds)
    }

    @Test
    fun `a missing copy is not treated as an empty one`() {
        assertSame(local, ProgressSnapshot.richer(remote = null, archived = local))
        assertSame(cloud, ProgressSnapshot.richer(remote = cloud, archived = null))
        // Both gone is the one honest "this account starts at level 1".
        assertNull(ProgressSnapshot.richer(remote = null, archived = null))
    }
}
