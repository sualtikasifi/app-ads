package com.sualtikasifi.cizimhafiza.domain.model

enum class GameMode {
    /** Standard timed drawing (5/7/10s by difficulty). */
    NORMAL,

    /** No countdown during drawing — advance to the next word manually. */
    RELAXED,

    /** Shorter drawing time than NORMAL for an extra challenge. */
    TIME_ATTACK
}
