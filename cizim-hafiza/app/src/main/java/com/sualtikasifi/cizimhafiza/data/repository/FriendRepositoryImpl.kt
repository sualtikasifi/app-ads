package com.sualtikasifi.cizimhafiza.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.domain.repository.CannotAddSelfException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendCodeNotFoundException
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
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
 * users/{uid}/friends/{friendUid} — { nickname, addedAt } — the friend's
 *                                    name is duplicated here so the list
 *                                    screen doesn't need N extra reads
 * users/{uid}/invites/{inviteId}  — { fromUid, fromNickname, roomCode, sentAt }
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

    override suspend fun sendMatchInvite(toUid: String, roomCode: String, myNickname: String) {
        val uid = requireUid()
        users.document(toUid).collection("invites").add(
            mapOf(
                "fromUid" to uid,
                "fromNickname" to myNickname,
                "roomCode" to roomCode,
                "sentAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun consumeInvite(inviteId: String) {
        val uid = requireUid()
        users.document(uid).collection("invites").document(inviteId).delete().await()
    }

    private fun generateFriendCode(): String = (100000..999999).random().toString()
}
