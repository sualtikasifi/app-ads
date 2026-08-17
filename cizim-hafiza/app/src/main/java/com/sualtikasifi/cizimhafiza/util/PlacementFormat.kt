package com.sualtikasifi.cizimhafiza.util

/** Medal emoji for the top 3 placements in a match/room leaderboard; null for 4th place and below. */
fun placementEmoji(placement: Int): String? = when (placement) {
    1 -> "🥇"
    2 -> "🥈"
    3 -> "🥉"
    else -> null
}
