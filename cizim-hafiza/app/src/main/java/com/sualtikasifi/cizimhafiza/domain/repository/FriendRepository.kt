package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.InviteEligibility
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import kotlinx.coroutines.flow.Flow

/** Firestore-backed friends list + match invites (see users/{uid}, friendCodes/{code}). */
interface FriendRepository {

    /** Returns this device's permanent friend code, generating one on first call. */
    suspend fun ensureFriendCode(nickname: String): String

    fun observeFriends(): Flow<List<Friend>>

    /** Looks up [code], and if found, adds each side to the other's friends list. */
    suspend fun addFriendByCode(code: String, myNickname: String): Result<Friend>

    /** Removes the friendship on both sides. */
    suspend fun removeFriend(friendUid: String): Result<Unit>

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
