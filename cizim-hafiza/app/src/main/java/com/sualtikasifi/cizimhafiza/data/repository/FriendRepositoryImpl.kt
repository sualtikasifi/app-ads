package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.InviteEligibility
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.repository.CannotAddSelfException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendCodeNotFoundException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firestore layout (see also OnlineGameRepositoryImpl.kt's comment for the
 * matching rooms/ shape):
 *
 * users/{uid}                     — { nickname, friendCode, createdAt }
 * friendCodes/{code}              — { uid }, a permanent (never rotates)
 *                                    twin of OnlineGameRepositoryImpl's
 *                                    rooms/{roomCode} code->doc lookup
 * users/{uid}/friends/{friendUid}      — { nickname, addedAt } — the friend's
 *                                         name is duplicated here so the list
 *                                         screen doesn't need N extra reads
 * users/{uid}/invites/{inviteId}       — { fromUid, fromNickname, roomCode, sentAt }
 * users/{uid}/blockedUsers/{blockedUid} — { nickname, blockedAt }
 * users/{uid}/inviteCooldowns/{fromUid} — { declinedAt } — see firestore.rules'
 *                                         invites create rule for the actual enforcement
 */
class FriendRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : FriendRepository {

    private val users get() = firestore.collection("users")
    private val friendCodes get() = firestore.collection("friendCodes")

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun ensureFriendCode(nickname: String): String {
        val uid = requireUid()
        val existing = users.document(uid).get().await().getString("friendCode")
        if (existing != null) return existing

        repeat(5) {
            val code = generateFriendCode()
            val codeRef = friendCodes.document(code)
            val created = firestore.runTransaction<Boolean> { tx ->
                if (tx.get(codeRef).exists()) {
                    false
                } else {
                    tx.set(codeRef, mapOf("uid" to uid))
                    tx.set(
                        users.document(uid),
                        mapOf(
                            "nickname" to nickname,
                            "friendCode" to code,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                    true
                }
            }.await()
            if (created) return code
        }
        throw IllegalStateException("Arkadaşlık kodu oluşturulamadı, tekrar dene")
    }

    override fun observeFriends(): Flow<List<Friend>> = callbackFlow {
        val uid = requireUid()
        val registration = users.document(uid).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val friends = snapshot?.documents?.mapNotNull { doc ->
                    val nickname = doc.getString("nickname") ?: return@mapNotNull null
                    Friend(uid = doc.id, nickname = nickname)
                } ?: emptyList()
                trySend(friends)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addFriendByCode(code: String, myNickname: String): Result<Friend> = runCatching {
        val uid = requireUid()
        val friendUid = friendCodes.document(code).get().await().getString("uid")
            ?: throw FriendCodeNotFoundException()
        if (friendUid == uid) throw CannotAddSelfException()
        val friendNickname = users.document(friendUid).get().await().getString("nickname") ?: "Oyuncu"

        val batch = firestore.batch()
        batch.set(
            users.document(uid).collection("friends").document(friendUid),
            mapOf("nickname" to friendNickname, "addedAt" to System.currentTimeMillis())
        )
        batch.set(
            users.document(friendUid).collection("friends").document(uid),
            mapOf("nickname" to myNickname, "addedAt" to System.currentTimeMillis())
        )
        batch.commit().await()
        Friend(uid = friendUid, nickname = friendNickname)
    }

    override suspend fun removeFriend(friendUid: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val batch = firestore.batch()
        batch.delete(users.document(uid).collection("friends").document(friendUid))
        batch.delete(users.document(friendUid).collection("friends").document(uid))
        batch.commit().await()
    }

    override fun observeIncomingInvites(): Flow<List<MatchInvite>> = callbackFlow {
        val uid = requireUid()
        val registration = users.document(uid).collection("invites")
            .orderBy("sentAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val invites = snapshot?.documents?.mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val fromNickname = doc.getString("fromNickname") ?: return@mapNotNull null
                    val roomCode = doc.getString("roomCode") ?: return@mapNotNull null
                    MatchInvite(
                        id = doc.id,
                        fromUid = fromUid,
                        fromNickname = fromNickname,
                        roomCode = roomCode,
                        sentAtMillis = doc.getLong("sentAt") ?: 0L
                    )
                } ?: emptyList()
                trySend(invites)
            }
        awaitClose { registration.remove() }
    }

    // UX-only pre-check — the real enforcement (which a modified client
    // can't bypass) is in firestore.rules' invites create rule. This just
    // lets the UI show "bu kişi seni engellemiş" / "N dakika bekle" instead
    // of a raw permission error, or nothing at all, before even trying.
    override suspend fun canInvite(toUid: String): InviteEligibility {
        val uid = requireUid()
        val blocked = users.document(toUid).collection("blockedUsers").document(uid).get().await()
        if (blocked.exists()) return InviteEligibility.Blocked

        val cooldown = users.document(toUid).collection("inviteCooldowns").document(uid).get().await()
        val declinedAt = cooldown.getLong("declinedAt") ?: return InviteEligibility.Eligible
        val remaining = (declinedAt + GameConstants.FRIEND_INVITE_COOLDOWN_MILLIS) - System.currentTimeMillis()
        return if (remaining > 0) InviteEligibility.OnCooldown(remaining) else InviteEligibility.Eligible
    }

    override suspend fun sendMatchInvite(toUid: String, roomCode: String, myNickname: String): Result<Unit> = runCatching {
        val uid = requireUid()
        users.document(toUid).collection("invites").add(
            mapOf(
                "fromUid" to uid,
                "fromNickname" to myNickname,
                "roomCode" to roomCode,
                "sentAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun consumeInvite(inviteId: String) {
        val uid = requireUid()
        users.document(uid).collection("invites").document(inviteId).delete().await()
    }

    override suspend fun declineInvite(invite: MatchInvite): Result<Unit> = runCatching {
        val uid = requireUid()
        val batch = firestore.batch()
        batch.delete(users.document(uid).collection("invites").document(invite.id))
        batch.set(
            users.document(uid).collection("inviteCooldowns").document(invite.fromUid),
            mapOf("declinedAt" to System.currentTimeMillis())
        )
        batch.commit().await()
    }

    override suspend fun blockUser(uid: String, nickname: String): Result<Unit> = runCatching {
        val myUid = requireUid()
        users.document(myUid).collection("blockedUsers").document(uid).set(
            mapOf("nickname" to nickname, "blockedAt" to System.currentTimeMillis())
        ).await()
    }

    override suspend fun unblockUser(uid: String): Result<Unit> = runCatching {
        val myUid = requireUid()
        users.document(myUid).collection("blockedUsers").document(uid).delete().await()
    }

    override fun observeBlockedUsers(): Flow<List<BlockedUser>> = callbackFlow {
        val uid = requireUid()
        val registration = users.document(uid).collection("blockedUsers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val blocked = snapshot?.documents?.mapNotNull { doc ->
                    val nickname = doc.getString("nickname") ?: return@mapNotNull null
                    BlockedUser(uid = doc.id, nickname = nickname, blockedAtMillis = doc.getLong("blockedAt") ?: 0L)
                } ?: emptyList()
                trySend(blocked)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateFcmToken(token: String) {
        val uid = requireUid()
        users.document(uid).set(mapOf("fcmToken" to token), SetOptions.merge()).await()
    }

    private fun generateFriendCode(): String = (100000..999999).random().toString()
}
