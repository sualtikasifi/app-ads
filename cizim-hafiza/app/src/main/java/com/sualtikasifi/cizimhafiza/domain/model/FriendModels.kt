package com.sualtikasifi.cizimhafiza.domain.model

data class Friend(val uid: String, val nickname: String)

/**
 * Somebody who entered your friend code and is waiting on your answer.
 *
 * Adding by code used to create the friendship outright, on both sides, from
 * the sender's device — so anyone who came by your six digits was in your
 * list whether you wanted them there or not, and the only remedy was to
 * notice and remove them. The code alone now only buys the right to ask.
 */
data class FriendRequest(val uid: String, val nickname: String, val sentAtMillis: Long)

/** What entering a friend code actually did — see FriendRepository.addFriendByCode. */
sealed interface AddFriendOutcome {
    /** The normal path: they now have a request waiting for them. */
    data class RequestSent(val nickname: String) : AddFriendOutcome

    /**
     * They had already asked you. Two people each holding the other's
     * request want the same thing, so the second one closes the loop
     * immediately rather than leaving both of them waiting on each other.
     */
    data class Added(val friend: Friend) : AddFriendOutcome

    /** Already in your list — nothing to send. */
    data class AlreadyFriends(val friend: Friend) : AddFriendOutcome
}

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
