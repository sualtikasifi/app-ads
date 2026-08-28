package com.sualtikasifi.cizimhafiza.domain.model

import com.sualtikasifi.cizimhafiza.R

/**
 * An unlockable, player-*chosen* ring illustration for [LevelAvatar]/
 * [presentation.common.LevelAvatar] — deliberately a separate ladder from
 * [LevelTier]. [LevelTier] still drives the fixed rank NAME text
 * ("Karalamacı"/"Çırak"/...) shown on StatisticsScreen; this enum drives only
 * the ring *graphic*, and unlike the tier (which always shows whatever band
 * the player's current level falls in) a frame stays available once earned —
 * a level-70 player can still wear the level-1 frame if they like it better,
 * see [resolve].
 *
 * [name] is persisted as the selected frame id (see
 * SettingsRepository.selectedAvatarFrameId), so — same rule as [Achievement]
 * — never rename an existing constant; add new ones instead.
 */
enum class AvatarFrame(val drawableRes: Int, val faceDiameterFraction: Float, val unlockLevel: Int) {
    SCRIBBLER(R.drawable.level_frame_scribbler, 0.70f, 1),
    ARTIST(R.drawable.level_frame_artist, 0.49f, 10),
    APPRENTICE(R.drawable.level_frame_apprentice, 0.40f, 20),
    POP_ART(R.drawable.level_frame_pop_art, 0.44f, 30),
    WOOD_PALETTE(R.drawable.level_frame_wood_palette, 0.44f, 40),
    MASTER_PAINTER(R.drawable.level_frame_master_painter, 0.40f, 50),
    WATERCOLOR_BRUSHES(R.drawable.level_frame_watercolor_brushes, 0.48f, 60),
    PAINTER(R.drawable.level_frame_painter, 0.54f, 70),
    CHALK(R.drawable.level_frame_chalk, 0.53f, 80),
    GRAFFITI(R.drawable.level_frame_graffiti, 0.49f, 90),
    GRAND_MASTER(R.drawable.level_frame_grand_master, 0.52f, 100);

    companion object {
        /** What every new install starts with, and what [resolve] falls back to. */
        val DEFAULT = SCRIBBLER

        /** Every frame this device has earned the right to wear at [level]. */
        fun unlockedFor(level: Int): List<AvatarFrame> = entries.filter { level >= it.unlockLevel }

        /** The most recently unlocked frame at [level] — used for players whose own pick we don't know (see [presentation.common.LevelAvatar]'s other-player call sites). */
        fun highestUnlockedFor(level: Int): AvatarFrame = entries.last { level >= it.unlockLevel }

        /**
         * The frame to actually render for *this* device's own player:
         * [selectedName] (SettingsRepository.selectedAvatarFrameId) if it
         * names a real frame this [level] has unlocked, otherwise
         * [DEFAULT] — never a locked frame, and never silently upgrading to
         * "whatever's newest" the way [LevelTier] does, since the whole
         * point is that the player picks.
         */
        fun resolve(selectedName: String?, level: Int): AvatarFrame {
            val selected = selectedName?.let { name -> entries.find { it.name == name } }
            return if (selected != null && level >= selected.unlockLevel) selected else DEFAULT
        }
    }
}
