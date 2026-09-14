package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * The 9 "worlds" of the level map — pure difficulty tiers now, not one per
 * word category. Each world's levels draw from every category at once (see
 * LevelCatalog.levelConfig passing a null category through to
 * GameRepository.getRandomWordsMix, the same "Tümü" path free play already
 * uses), so climbing the map means facing a broad, homogeneous mix of
 * subjects instead of ten levels of nothing but animals, then ten of
 * nothing but food. What actually separates one world from the next is
 * difficulty (see LevelCatalog's world-shifted difficultyMixFor), so these
 * are themed as stages of an artist's own growth rather than as places.
 *
 * [name] is not persisted anywhere that survives a rename (level progress
 * keys off [id], not the enum constant name), so unlike [Achievement]/
 * [AvatarFrame]/[PenSkin] there is no rename hazard here.
 */
enum class World(
    val id: Int,
    val emoji: String,
    @StringRes val displayNameRes: Int,
    val accentColor: Long,
    @DrawableRes val iconRes: Int
) {
    WORLD_1(1, "🖍️", R.string.world_name_1, 0xFFEF9A3C, R.drawable.world_icon_1),
    WORLD_2(2, "🌱", R.string.world_name_2, 0xFF7CB342, R.drawable.world_icon_2),
    WORLD_3(3, "🎨", R.string.world_name_3, 0xFF5C6BC0, R.drawable.world_icon_3),
    WORLD_4(4, "💭", R.string.world_name_4, 0xFF26A69A, R.drawable.world_icon_4),
    WORLD_5(5, "🖌️", R.string.world_name_5, 0xFFEC407A, R.drawable.world_icon_5),
    WORLD_6(6, "🏔️", R.string.world_name_6, 0xFF546E7A, R.drawable.world_icon_6),
    WORLD_7(7, "🌀", R.string.world_name_7, 0xFF8D6E63, R.drawable.world_icon_7),
    WORLD_8(8, "🧠", R.string.world_name_8, 0xFF5E35B1, R.drawable.world_icon_8),
    WORLD_9(9, "👑", R.string.world_name_9, 0xFFD4A02A, R.drawable.world_icon_9);

    companion object {
        fun forId(id: Int): World? = entries.find { it.id == id }
    }
}
