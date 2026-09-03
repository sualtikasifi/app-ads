package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.sualtikasifi.cizimhafiza.data.bot.BotRoomEngine
import com.sualtikasifi.cizimhafiza.domain.model.AddFriendOutcome
import com.sualtikasifi.cizimhafiza.domain.model.BlockedUser
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.FriendRequest
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
 * users/{uid}/friendRequests/{fromUid} — { fromNickname, sentAt } — waiting on
 *                                         this user's yes or no; only they can
 *                                         turn one into a friends/ entry
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

    /**
     * When each league member's profile was last read from the server, so
     * the fan-out below can be served from the (free) local cache in
     * between.
     *
     * The table used to cost one billed read per friend *every* time it was
     * opened, and again on every change to the friends list — so a player
     * with 10 friends who checks the standings five times a day paid ~55
     * reads a day for a number that changes when someone finishes a game.
     * A short server window keeps it honest without paying for the rest:
     * the entries themselves are weekly XP totals, and the one row that
     * moves fastest — the player's own — is written by this device (see
     * publishWeeklyScore, called on every League open) and therefore always
     * reads back fresh from the cache regardless of this window.
     */
    private val profileFetchedAtMillis = mutableMapOf<String, Long>()

    /** This device's own friend code, once known — see ensureFriendCode. */
    @Volatile
    private var cachedFriendCode: String? = null

    private suspend fun readLeagueProfile(memberUid: String): DocumentSnapshot? {
        val doc = users.document(memberUid)
        val lastFetch = profileFetchedAtMillis[memberUid]
        if (lastFetch != null && System.currentTimeMillis() - lastFetch < PROFILE_CACHE_TTL_MILLIS) {
            // A cache miss here is not an error worth surfacing — fall
            // through to the server read the profile clearly needs.
            runCatching { doc.get(Source.CACHE).await() }
                .getOrNull()
                ?.takeIf { it.exists() }
                ?.let { return it }
        }
        val fresh = doc.get(Source.SERVER).await()
        profileFetchedAtMillis[memberUid] = System.currentTimeMillis()
        return fresh
    }

    private suspend fun requireUid(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: throw IllegalStateException("Firebase oturumu açılamadı")
    }

    override suspend fun ensureFriendCode(nickname: String): String {
        val uid = requireUid()
        // A friend code is assigned once and never rotates (see the class
        // doc), so re-reading it from the server every time the Friends
        // screen opens paid for an answer that cannot have changed. Cached
        // in memory first, then from Firestore's local cache, and only from
        // the server when this device genuinely has never seen it.
        cachedFriendCode?.let { return it }
        val meDoc = users.document(uid)
        val cached = runCatching { meDoc.get(Source.CACHE).await() }.getOrNull()?.getString("friendCode")
        if (cached != null) {
            cachedFriendCode = cached
            return cached
        }
        val existing = meDoc.get(Source.SERVER).await().getString("friendCode")
        if (existing != null) {
            cachedFriendCode = existing
            return existing
        }

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
            if (created) {
                cachedFriendCode = code
                return code
            }
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

    override suspend fun addFriendByCode(code: String, myNickname: String): Result<AddFriendOutcome> = runCatching {
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
        val friend = Friend(uid = friendUid, nickname = friendNickname)

        // Already friends: say so rather than sending a request that would
        // sit in their list asking for something they already gave.
        if (users.document(uid).collection("friends").document(friendUid).get().await().exists()) {
            return@runCatching AddFriendOutcome.AlreadyFriends(friend)
        }

        // They asked first. Two people each holding the other's request want
        // the same thing, so this closes the loop instead of leaving them
        // both waiting — and it is the only path here that writes a
        // friendship, because consent from both sides already exists.
        val theirRequest = users.document(uid).collection("friendRequests").document(friendUid).get().await()
        if (theirRequest.exists()) {
            writeFriendship(uid, myNickname, friendUid, friendNickname, clearRequestOn = uid)
            return@runCatching AddFriendOutcome.Added(friend)
        }

        // The normal path. One write, into THEIR inbox, carrying nothing but
        // who is asking — the friendship itself is theirs to create.
        users.document(friendUid).collection("friendRequests").document(uid).set(
            mapOf("fromNickname" to myNickname, "sentAt" to System.currentTimeMillis())
        ).await()
        AddFriendOutcome.RequestSent(friendNickname)
    }

    override fun observeFriendRequests(): Flow<List<FriendRequest>> =
        firestoreFlow("friendRequests") { emit, onError ->
            val uid = requireUid()
            users.document(uid).collection("friendRequests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        return@addSnapshotListener
                    }
                    val requests = snapshot?.documents?.map { doc ->
                        FriendRequest(
                            uid = doc.id,
                            nickname = doc.getString("fromNickname").orEmpty().ifBlank { "Oyuncu" },
                            sentAtMillis = doc.getLong("sentAt") ?: 0L
                        )
                    }?.sortedBy { it.sentAtMillis } ?: emptyList()
                    emit(requests)
                }
        }

    override suspend fun acceptFriendRequest(request: FriendRequest, myNickname: String): Result<Unit> = runCatching {
        val uid = requireUid()
        writeFriendship(uid, myNickname, request.uid, request.nickname, clearRequestOn = uid)
    }

    override suspend fun declineFriendRequest(fromUid: String): Result<Unit> = runCatching {
        val uid = requireUid()
        users.document(uid).collection("friendRequests").document(fromUid).delete().await()
    }

    /**
     * Both halves of a friendship plus the request that authorised it, in one
     * batch. Writing the two friends/ documents separately would leave a
     * window where one person has a friend the other does not.
     */
    private suspend fun writeFriendship(
        uid: String,
        myNickname: String,
        friendUid: String,
        friendNickname: String,
        clearRequestOn: String
    ) {
        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.set(
            users.document(uid).collection("friends").document(friendUid),
            mapOf("nickname" to friendNickname, "addedAt" to now)
        )
        batch.set(
            users.document(friendUid).collection("friends").document(uid),
            mapOf("nickname" to myNickname, "addedAt" to now)
        )
        batch.delete(users.document(clearRequestOn).collection("friendRequests").document(friendUid))
        batch.commit().await()
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
                                val doc = readLeagueProfile(memberUid)
                                if (doc == null || !doc.exists()) return@mapNotNull null
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
        /** See profileFetchedAtMillis — long enough to collapse a burst of table opens, short enough that a friend's finished game shows up the same session. */
        const val PROFILE_CACHE_TTL_MILLIS = 10 * 60 * 1000L
        const val PRIVATE_COLLECTION = "private"
        const val DEVICE_DOC = "device"
    }

    private fun generateFriendCode(): String = (100000..999999).random().toString()
}
