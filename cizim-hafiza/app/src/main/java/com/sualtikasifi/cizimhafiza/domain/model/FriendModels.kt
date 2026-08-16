package com.sualtikasifi.cizimhafiza.domain.model

data class Friend(val uid: String, val nickname: String)

/** An online-match invite one friend sent another, waiting to be accepted. */
data class MatchInvite(
    val id: String,
    val fromUid: String,
    val fromNickname: String,
    val roomCode: String,
    val sentAtMillis: Long
)
