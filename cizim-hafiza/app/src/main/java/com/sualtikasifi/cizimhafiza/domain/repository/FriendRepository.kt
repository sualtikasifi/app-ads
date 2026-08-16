package com.sualtikasifi.cizimhafiza.domain.repository

import com.sualtikasifi.cizimhafiza.domain.model.Friend
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import kotlinx.coroutines.flow.Flow

/** Firestore-backed friends list + match invites (see users/{uid}, friendCodes/{code}). */
interface FriendRepository {

    /** Returns this device's permanent friend code, generating one on first call. */
    suspend fun ensureFriendCode(nickname: String): String

    fun observeFriends(): Flow<List<Friend>>

    /** Looks up [code], and if found, adds each side to the other's friends list. */
    suspend fun addFriendByCode(code: String, myNickname: String): Result<Friend>

    fun observeIncomingInvites(): Flow<List<MatchInvite>>

    suspend fun sendMatchInvite(toUid: String, roomCode: String, myNickname: String)

    /** Removes an invite once it's been accepted or dismissed. */
    suspend fun consumeInvite(inviteId: String)
}

class FriendCodeNotFoundException : Exception("Bu kodla bir kullanıcı bulunamadı")
class CannotAddSelfException : Exception("Kendini arkadaş olarak ekleyemezsin")
