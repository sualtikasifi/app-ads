package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * A cosmetic pen the player draws with — the second unlockable track
 * alongside [AvatarFrame].
 *
 * The frame ladder alone gives eleven rewards spread across a hundred
 * levels, so most of the climb passes with nothing to show for it. Pens fill
 * that gap: they unlock on the *odd* fives (5, 15, 25 …) precisely so they
 * never land on the same level as a frame, turning one reward every ten
 * levels into one every five.
 *
 * Unlike a frame, a pen is visible in the thing the player is actually
 * doing — their own strokes — and it is visible to opponents too, because
 * the drawing is what the other player has to guess (see
 * OnlinePlayer.penSkinId).
 *
 * The `name` of each constant is persisted (SettingsRepository's selected
 * pen, and the online player entry), so **never rename one** — a rename
 * silently resets every player wearing it. AvatarFrameTest's equivalent
 * guard applies here too, see PenSkinTest.
 */
enum class PenSkin(
    @StringRes val labelRes: Int,
    /** ARGB colours the stroke is drawn with. One entry paints flat; more blend along the stroke. */
    val colors: List<Long>,
    val unlockLevel: Int,
    /**
     * A weekly-league prize rather than a rung on the level ladder.
     *
     * These are excluded from [unlockedFor], so no amount of levelling
     * offers one, and the picker only shows them to a player who actually
     * earned one (SettingsRepository.earnedLeagueRewardIds).
     *
     * [resolve] deliberately does NOT check ownership. It is called for
     * OTHER players' pens too, arriving from their device over Firestore,
     * and this device has no way to know what somebody else won — refusing
     * on that basis would silently repaint every league winner's strokes
     * back to the default in everyone else's game. The trade-off is that a
     * player who edits their own preferences file can wear one unearned;
     * that is cosmetic, invisible to scoring, and cheaper to accept than a
     * server round-trip per opponent.
     */
    val isLeagueReward: Boolean = false
) {
    CLASSIC(R.string.pen_classic, listOf(0xFF2E2A26), 1),
    // The enum constant keeps its name — it is persisted, and renaming it
    // would reset every player wearing it (see the class doc). Only the
    // colour and label changed: at 0xFF4A4A4A this was a near-black barely
    // a shade off CLASSIC, so the first pen the game ever hands out looked
    // like no reward at all. A true mid-grey reads as a different pencil.
    CHARCOAL(R.string.pen_charcoal, listOf(0xFF8A9199), 5),
    OCEAN(R.string.pen_ocean, listOf(0xFF1B7A8C), 15),
    SUNSET(R.string.pen_sunset, listOf(0xFFEB7A3C, 0xFFE24B6A), 25),
    FOREST(R.string.pen_forest, listOf(0xFF2E7D4F), 35),
    BERRY(R.string.pen_berry, listOf(0xFF8E3A8E), 45),
    GOLD(R.string.pen_gold, listOf(0xFFD4A02A, 0xFFF2D06B), 55),
    NEON(R.string.pen_neon, listOf(0xFF00E5A0, 0xFF00B8D4), 65),
    LAVA(R.string.pen_lava, listOf(0xFFFF3D00, 0xFFFFC107), 75),
    GALAXY(R.string.pen_galaxy, listOf(0xFF5B3FA8, 0xFF9B5DE5, 0xFF3FA8D4), 85),
    RAINBOW(R.string.pen_rainbow, listOf(0xFFE24B4B, 0xFFEB9A3C, 0xFF3FA85E, 0xFF3F7AD4, 0xFF8E3A8E), 95),

    // --- Weekly league prizes (see LeagueReward) ---
    //
    // unlockLevel 0 so resolve() renders them for anybody already wearing
    // one; isLeagueReward is what actually keeps them off the level ladder.
    // Deliberately louder than the level pens: the whole point of a prize
    // nobody can grind for is that it is recognisable in somebody else's
    // drawing.
    LEAGUE_AURORA(R.string.pen_league_aurora, listOf(0xFF2AF598, 0xFF08AEEA, 0xFF6A5AE0), 0, isLeagueReward = true),
    LEAGUE_EMBER(R.string.pen_league_ember, listOf(0xFFFF5F6D, 0xFFFFC371), 0, isLeagueReward = true),
    LEAGUE_FROST(R.string.pen_league_frost, listOf(0xFFB8F2FF, 0xFF4FACFE, 0xFF1B3F8B), 0, isLeagueReward = true),
    LEAGUE_MIDNIGHT(R.string.pen_league_midnight, listOf(0xFF232526, 0xFF6D5BBA, 0xFF8D58BF), 0, isLeagueReward = true),
    LEAGUE_CITRUS(R.string.pen_league_citrus, listOf(0xFFF9D423, 0xFFFF4E50), 0, isLeagueReward = true),
    LEAGUE_ROSE(R.string.pen_league_rose, listOf(0xFFFFDEE9, 0xFFE96D9E, 0xFF8E2DE2), 0, isLeagueReward = true);

    /** True when the stroke should be painted as a gradient rather than a flat colour. */
    val isGradient: Boolean get() = colors.size > 1

    companion object {
        val DEFAULT = CLASSIC

        /** Every pen on the level ladder, in unlock order — league prizes excluded. */
        val ladder: List<PenSkin> get() = entries.filter { !it.isLeagueReward }

        /** The level ladder only — league prizes are earned, not reached. */
        fun unlockedFor(level: Int): List<PenSkin> =
            ladder.filter { level >= it.unlockLevel }

        /**
         * The stored pick, but only if this [level] has actually earned it —
         * same defensive reasoning as [AvatarFrame.resolve]: the selection
         * lives in plain SharedPreferences and arrives from an opponent's
         * device over Firestore, so neither source can be trusted to be legal.
         */
        fun resolve(selectedName: String?, level: Int): PenSkin {
            val selected = selectedName?.let { name -> entries.find { it.name == name } }
            return if (selected != null && level >= selected.unlockLevel) selected else DEFAULT
        }
    }
}
