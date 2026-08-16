package com.sualtikasifi.cizimhafiza.domain.model

/** Art-themed rank tiers unlocked by cumulative lifetime score (see SettingsRepository.lifetimeScore). */
enum class PlayerRank(val displayName: String, val emoji: String, val minScore: Int) {
    KARALAMACI("Karalamacı", "✏️", 0),
    CIRAK("Çırak", "🖊️", 1000),
    RESSAM("Ressam", "🖌️", 3000),
    USTA_RESSAM("Usta Ressam", "🎨", 5000),
    SANATCI("Sanatçı", "🖼️", 10000),
    BUYUK_USTA("Büyük Usta", "👑", 25000);

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
