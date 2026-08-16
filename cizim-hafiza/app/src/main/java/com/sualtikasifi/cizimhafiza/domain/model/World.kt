package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * The 9 "worlds" of the level map — one per existing word category (see
 * WordDao.getRandomWords), so a world needs zero new word content. [category]
 * must match the DB's category strings exactly (e.g. "Hayvanlar", "Spor").
 */
enum class World(
    val id: Int,
    val category: String,
    val emoji: String,
    @StringRes val displayNameRes: Int,
    val accentColor: Long
) {
    HAYVANLAR(1, "Hayvanlar", "🐶", R.string.world_name_hayvanlar, 0xFF7CB342),
    ESYALAR(2, "Eşyalar", "🧺", R.string.world_name_esyalar, 0xFF8D6E63),
    MESLEKLER(3, "Meslekler", "👮", R.string.world_name_meslekler, 0xFF5C6BC0),
    SPOR(4, "Spor", "⚽", R.string.world_name_spor, 0xFFEF6C00),
    DOGA(5, "Doğa", "🌲", R.string.world_name_doga, 0xFF2E7D32),
    YIYECEKLER(6, "Yiyecekler", "🍎", R.string.world_name_yiyecekler, 0xFFE53935),
    TASITLAR(7, "Taşıtlar", "🚗", R.string.world_name_tasitlar, 0xFF546E7A),
    DUYGULAR(8, "Duygular", "😊", R.string.world_name_duygular, 0xFFEC407A),
    GIYIM(9, "Giyim", "👕", R.string.world_name_giyim, 0xFF00897B);

    companion object {
        fun forId(id: Int): World? = entries.find { it.id == id }
    }
}
