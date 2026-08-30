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

data class BlockedUser(val uid: String, val nickname: String, val blockedAtMillis: Long)

/** Result of checking whether the current user may invite [toUid] right now — see FriendRepository.canInvite. */
sealed interface InviteEligibility {
    data object Eligible : InviteEligibility
    data object Blocked : InviteEligibility
    data class OnCooldown(val remainingMillis: Long) : InviteEligibility
}
