package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * The art-themed title shown alongside the player's level.
 *
 * These used to be unlocked directly by cumulative lifetime score. They're
 * now the names of [LevelTier]'s bands instead — see [PlayerLevel] for why
 * the app has a single XP-driven ladder rather than a score ladder and a
 * level ladder climbing in parallel.
 */
enum class PlayerRank(@StringRes val nameRes: Int, val emoji: String) {
    KARALAMACI(R.string.rank_karalamaci, "✏️"),
    CIRAK(R.string.rank_cirak, "🖊️"),
    RESSAM(R.string.rank_ressam, "🖌️"),
    USTA_RESSAM(R.string.rank_usta_ressam, "🎨"),
    SANATCI(R.string.rank_sanatci, "🖼️"),
    BUYUK_USTA(R.string.rank_buyuk_usta, "👑")
}
