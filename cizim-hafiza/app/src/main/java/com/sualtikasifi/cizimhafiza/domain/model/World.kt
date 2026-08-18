package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * The 9 "worlds" of the level map — one per existing word category (see
 * WordDao.getRandomWords), so a world needs zero new word content.
 * [category]/[categoryEn] must match the DB's `words.category` strings
 * exactly for their respective language (e.g. "Hayvanlar" when
 * WordSeeder.currentLanguage() == "tr", "Animals" when == "en") — the
 * `words` table's category column is re-seeded per-language (see
 * WordPoolSynchronizer), so a query using the wrong language's category
 * string matches nothing (or, if ids happen to still hold stale data from
 * before a re-seed, the OTHER language's text). Always resolve through
 * [categoryFor], never read [category] directly for a DB query.
 */
enum class World(
    val id: Int,
    val category: String,
    val categoryEn: String,
    val emoji: String,
    @StringRes val displayNameRes: Int,
    val accentColor: Long
) {
    HAYVANLAR(1, "Hayvanlar", "Animals", "🐶", R.string.world_name_hayvanlar, 0xFF7CB342),
    ESYALAR(2, "Eşyalar", "Objects", "🧺", R.string.world_name_esyalar, 0xFF8D6E63),
    MESLEKLER(3, "Meslekler", "Professions", "👮", R.string.world_name_meslekler, 0xFF5C6BC0),
    SPOR(4, "Spor", "Sports", "⚽", R.string.world_name_spor, 0xFFEF6C00),
    DOGA(5, "Doğa", "Nature", "🌲", R.string.world_name_doga, 0xFF2E7D32),
    YIYECEKLER(6, "Yiyecekler", "Food", "🍎", R.string.world_name_yiyecekler, 0xFFE53935),
    TASITLAR(7, "Taşıtlar", "Vehicles", "🚗", R.string.world_name_tasitlar, 0xFF546E7A),
    DUYGULAR(8, "Duygular", "Emotions", "😊", R.string.world_name_duygular, 0xFFEC407A),
    GIYIM(9, "Giyim", "Clothing", "👕", R.string.world_name_giyim, 0xFF00897B);

    /** The `words.category` value to query for, given "tr"/"en" (see WordSeeder.currentLanguage). */
    fun categoryFor(language: String): String = if (language == "en") categoryEn else category

    companion object {
        fun forId(id: Int): World? = entries.find { it.id == id }
    }
}
