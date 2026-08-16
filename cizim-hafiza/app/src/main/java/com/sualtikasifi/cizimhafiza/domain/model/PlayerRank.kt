package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/** Art-themed rank tiers unlocked by cumulative lifetime score (see SettingsRepository.lifetimeScore). */
enum class PlayerRank(@StringRes val nameRes: Int, val emoji: String, val minScore: Int) {
    KARALAMACI(R.string.rank_karalamaci, "✏️", 0),
    CIRAK(R.string.rank_cirak, "🖊️", 1000),
    RESSAM(R.string.rank_ressam, "🖌️", 3000),
    USTA_RESSAM(R.string.rank_usta_ressam, "🎨", 5000),
    SANATCI(R.string.rank_sanatci, "🖼️", 10000),
    BUYUK_USTA(R.string.rank_buyuk_usta, "👑", 25000);

    companion object {
        fun forScore(score: Int): PlayerRank = entries.last { score >= it.minScore }
    }
}

data class PlayerProgress(
    val lifetimeScore: Int,
    val rank: PlayerRank,
    val nextRank: PlayerRank?,
    val progressFraction: Float
) {
    companion object {
        fun forScore(score: Int): PlayerProgress {
            val rank = PlayerRank.forScore(score)
            val next = PlayerRank.entries.getOrNull(rank.ordinal + 1)
            val fraction = if (next == null) {
                1f
            } else {
                (score - rank.minScore).toFloat() / (next.minScore - rank.minScore).toFloat()
            }
            return PlayerProgress(score, rank, next, fraction.coerceIn(0f, 1f))
        }
    }
}
