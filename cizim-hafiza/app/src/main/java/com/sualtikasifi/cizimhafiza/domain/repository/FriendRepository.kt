package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.AddFriendOutcome
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.FriendRequest
import com.sualtikasifi.cizimhafiza.domain.model.InviteEligibility
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import kotlinx.coroutines.flow.Flow

/** Firestore-backed friends list + match invites (see users/{uid}, friendCodes/{code}). */
interface FriendRepository {

    /** Returns this device's permanent friend code, generating one on first call. */
    suspend fun ensureFriendCode(nickname: String): String

    fun observeFriends(): Flow<List<Friend>>

    /**
     * Looks up [code] and asks that player to be friends — it does NOT make
     * them one. See [AddFriendOutcome] for the three ways that can land.
     */
    suspend fun addFriendByCode(code: String, myNickname: String): Result<AddFriendOutcome>

    /** Requests waiting on this player's answer — see [acceptFriendRequest]. */
    fun observeFriendRequests(): Flow<List<FriendRequest>>

    /** Writes the friendship onto both sides and clears the request. */
    suspend fun acceptFriendRequest(request: FriendRequest, myNickname: String): Result<Unit>

    /** Drops the request without a trace. No cooldown, no notification — see declineInvite for the noisier case. */
    suspend fun declineFriendRequest(fromUid: String): Result<Unit>

    /** Removes the friendship on both sides. */
    suspend fun removeFriend(friendUid: String): Result<Unit>

    /**
     * Reads this uid's current friends and detaches it from every one of
     * them — called right before this identity is abandoned in favour of a
     * different, pre-existing account (see
     * AuthRepository.switchToExistingAccount). Must run WHILE STILL SIGNED
     * IN as this uid: firestore.rules only lets a friend remove THEMSELVES
     * from someone else's list, and once this uid stops being the active
     * session it can never do that again — an unmigrated friendship would
     * sit forever pointing at an identity nobody uses.
     *
     * The returned list is what [adoptMigratedFriends] re-establishes on
     * the account being switched to.
     */
    suspend fun prepareFriendMigration(): List<Friend>

    /**
     * The other half of a migration started by [prepareFriendMigration] —
     * called once already signed in as the NEW uid. Adds each migrated
     * friend onto this uid's own list and this uid onto theirs, the same
     * "write both sides from whichever client is present" pattern
     * [acceptFriendRequest] already uses. Best-effort: one friend that
     * fails to migrate (blocked in the meantime, deleted their account)
     * must not undo the ones that succeeded, and must never block the
     * account switch itself.
     */
    suspend fun adoptMigratedFriends(friends: List<Friend>)

    fun observeIncomingInvites(): Flow<List<MatchInvite>>

    /** Pre-send check (UX only — the real enforcement is in firestore.rules' invites create rule). */
    suspend fun canInvite(toUid: String): InviteEligibility

    suspend fun sendMatchInvite(toUid: String, roomCode: String, myNickname: String): Result<Unit>

    /** Removes an invite once it's been accepted — no cooldown consequence (see declineInvite for that). */
    suspend fun consumeInvite(inviteId: String)

    /** Removes the invite and starts a cooldown so [invite]'s sender can't re-invite for a while. */
    suspend fun declineInvite(invite: MatchInvite): Result<Unit>

    suspend fun blockUser(uid: String, nickname: String): Result<Unit>
    suspend fun unblockUser(uid: String): Result<Unit>
    fun observeBlockedUsers(): Flow<List<BlockedUser>>

    /** Persists this device's current FCM token so the invite Cloud Function can push to it. */
    suspend fun updateFcmToken(token: String)

    /**
     * Publishes this device's weekly-league standing onto its own public
     * profile document, so friends can read it without a per-player
     * subcollection. Safe to call often — it is a single merged write.
     */
    suspend fun publishWeeklyScore(nickname: String, weeklyXp: Int, weekId: Long, level: Int, frameId: String)

    /**
     * The player's own row plus every friend's, already ranked — see
     * domain.model.LeagueTable. Rows from a previous week read as zero
     * rather than being hidden, so the table is complete on a Monday
     * morning instead of empty.
     */
    fun observeLeagueTable(): Flow<LeagueTable>
}

class FriendCodeNotFoundException : Exception("Bu kodla bir kullanıcı bulunamadı")
class CannotAddSelfException : Exception("Kendini arkadaş olarak ekleyemezsin")

/**
 * Thrown by [FriendRepository.addFriendByCode] for the bot's fixed code
 * (see BotRoomEngine.ROOM_CODE) — never actually adds a friendship, always
 * looks like a request that was sent and is still awaiting a response, so
 * the bot reads as a cautious real person who hasn't answered yet, not an
 * outright rejection.
 */
class BotFriendRequestPendingException : Exception("İstek gönderildi, henüz cevap yok")
