package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.InviteEligibility
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.repository.BotFriendRequestPendingException
import com.sualtikasifi.cizimhafiza.domain.repository.CannotAddSelfException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendCodeNotFoundException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.util.GameConstants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LeagueEntry
import com.sualtikasifi.cizimhafiza.domain.model.LeagueTable
import com.sualtikasifi.cizimhafiza.domain.model.WeeklyLeague
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    // The league table needs a suspend fan-out (one profile read per friend)
    // from inside a non-suspending snapshot callback. Scoped to this
    // singleton repository rather than a caller's scope so a screen going
    // away mid-fetch cannot cancel a write it did not start.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun observeFriends(): Flow<List<Friend>> = firestoreFlow("friends") { emit, onError ->
        val uid = requireUid()
        users.document(uid).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val friends = snapshot?.documents?.mapNotNull { doc ->
                    val nickname = doc.getString("nickname") ?: return@mapNotNull null
                    Friend(uid = doc.id, nickname = nickname)
                } ?: emptyList()
                emit(friends)
            }
    }

    override suspend fun addFriendByCode(code: String, myNickname: String): Result<Friend> = runCatching {
        // The bot's room code isn't a real friend code (no friendCodes/130246
        // doc exists, and can't — creating one would need a real signed-in
        // "karalak-bot" Auth user, which doesn't exist). Intercepted here,
        // before ever touching Firestore, so it always reads as a request
        // that was sent and is still pending — see the exception's doc.
        if (code == BotRoomEngine.ROOM_CODE) throw BotFriendRequestPendingException()

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

    override fun observeIncomingInvites(): Flow<List<MatchInvite>> = firestoreFlow("invites") { emit, onError ->
        val uid = requireUid()
        users.document(uid).collection("invites")
            .orderBy("sentAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
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
                emit(invites)
            }
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

    override fun observeBlockedUsers(): Flow<List<BlockedUser>> = firestoreFlow("blockedUsers") { emit, onError ->
        val uid = requireUid()
        users.document(uid).collection("blockedUsers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val blocked = snapshot?.documents?.mapNotNull { doc ->
                    val nickname = doc.getString("nickname") ?: return@mapNotNull null
                    BlockedUser(uid = doc.id, nickname = nickname, blockedAtMillis = doc.getLong("blockedAt") ?: 0L)
                } ?: emptyList()
                emit(blocked)
            }
    }

    override suspend fun updateFcmToken(token: String) {
        val uid = requireUid()
        // users/{uid} itself is world-readable to any signed-in device — that
        // is what lets a friend resolve your nickname and badge. A push token
        // has no business being in a document with those read rules: it
        // identifies a specific physical device, and every other player could
        // read it. It lives in the owner-only private/ subcollection instead
        // (see firestore.rules). The Cloud Function that sends the push runs
        // with admin credentials, which bypass rules, so nothing is lost on
        // the delivery side — keep functions/src/index.ts's read path
        // pointing here.
        users.document(uid)
            .collection(PRIVATE_COLLECTION)
            .document(DEVICE_DOC)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .await()
    }

    override suspend fun publishWeeklyScore(
        nickname: String,
        weeklyXp: Int,
        weekId: Long,
        level: Int,
        frameId: String
    ) {
        val uid = requireUid()
        // Merged onto the public profile document rather than a subcollection:
        // users/{uid} is already world-readable to any signed-in device (that
        // is how a friend list resolves nicknames), so a friend can build the
        // whole table with one read per friend instead of a read plus a
        // profile lookup each. Nothing private goes in here — the FCM token
        // lives in private/device for exactly that reason.
        users.document(uid).set(
            mapOf(
                "nickname" to nickname,
                "weeklyXp" to weeklyXp,
                "weekId" to weekId,
                "level" to level,
                "frameId" to frameId
            ),
            SetOptions.merge()
        ).await()
    }

    override fun observeLeagueTable(): Flow<LeagueTable> =
        firestoreFlow("leagueTable") { emit, onError ->
            val uid = requireUid()
            val currentWeek = WeeklyLeague.weekIdFor(LocalDate.now().toEpochDay())
            val daysRemaining = WeeklyLeague.daysRemainingIn(LocalDate.now().toEpochDay())

            // Driven off the friends list rather than a query across all
            // users: there is no index that could scope "everyone I am
            // friends with" server-side, and the list is small enough that
            // reading each profile is cheaper than any alternative.
            users.document(uid).collection("friends")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        return@addSnapshotListener
                    }
                    val friendUids = snapshot?.documents?.map { it.id }.orEmpty()
                    scope.launch {
                        val rows = runCatching {
                            (friendUids + uid).distinct().mapNotNull { memberUid ->
                                val doc = users.document(memberUid).get().await()
                                if (!doc.exists()) return@mapNotNull null
                                // A profile still stamped with last week's id
                                // has simply not played yet this week — show
                                // it at zero rather than dropping the row, so
                                // the table is complete on a Monday morning
                                // instead of nearly empty.
                                val storedWeek = doc.getLong("weekId") ?: -1L
                                LeagueEntry(
                                    uid = memberUid,
                                    nickname = doc.getString("nickname").orEmpty().ifBlank { "?" },
                                    weeklyXp = if (storedWeek == currentWeek) (doc.getLong("weeklyXp") ?: 0L).toInt() else 0,
                                    level = (doc.getLong("level") ?: 1L).toInt(),
                                    frameId = doc.getString("frameId") ?: AvatarFrame.DEFAULT.name,
                                    isMe = memberUid == uid
                                )
                            }
                        }.getOrDefault(emptyList())
                        emit(LeagueTable.rank(rows, daysRemaining))
                    }
                }
        }

    private companion object {
        const val PRIVATE_COLLECTION = "private"
        const val DEVICE_DOC = "device"
    }

    private fun generateFriendCode(): String = (100000..999999).random().toString()
}
