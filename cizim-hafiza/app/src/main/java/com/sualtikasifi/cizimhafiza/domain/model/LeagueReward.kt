package com.sualtikasifi.cizimhafiza.domain.model

/**
 * A cosmetic handed to the weekly global league's top three.
 *
 * Deliberately NOT a second catalog. A reward is just a pointer at a
 * cosmetic already marked [PenSkin.isLeagueReward] / [AvatarFrame.isLeagueReward],
 * so adding a prize is adding the cosmetic and nothing else — there is no
 * parallel list to forget to update, and no way for the two to disagree
 * about what exists.
 *
 * The [id] is what travels: it is stored in Firestore (leaderboards/config,
 * set from the review panel), written into each winner's record by the
 * finalizeWeeklyLeague function, and persisted on the device as an earned
 * reward. So **an id must never change** once a week has been played under
 * it, for the same reason the enum constants behind it must not be renamed.
 */
sealed interface LeagueReward {

    val id: String

    /**
     * A pen carries a name of its own ([PenSkin.labelRes]).
     *
     * A frame does not, and is not given one here: frames have never been
     * named anywhere in the app — the picker shows the artwork and the
     * artwork is the whole label — so inventing names only for the league
     * ones would make them the odd entries out in their own picker.
     */
    data class Pen(val skin: PenSkin) : LeagueReward {
        override val id: String get() = "$PEN_PREFIX${skin.name}"
    }

    data class Frame(val frame: AvatarFrame) : LeagueReward {
        override val id: String get() = "$FRAME_PREFIX${frame.name}"
    }

    companion object {
        // Prefixed so a pen and a frame that happen to share a constant name
        // cannot collide on one id.
        const val PEN_PREFIX = "PEN:"
        const val FRAME_PREFIX = "FRAME:"

        /** Every cosmetic that can be set as a week's prize. */
        val all: List<LeagueReward>
            get() = PenSkin.entries.filter { it.isLeagueReward }.map { Pen(it) } +
                AvatarFrame.entries.filter { it.isLeagueReward }.map { Frame(it) }

        /**
         * Resolves a stored id, or null if it names nothing this build has.
         *
         * Null is a real case, not a defect: a device still on an older
         * version will read a reward id for artwork it does not ship yet.
         * Everything that handles a reward has to survive not recognising
         * one — which is why this returns null rather than a placeholder.
         */
        fun find(id: String?): LeagueReward? {
            if (id.isNullOrBlank()) return null
            return all.firstOrNull { it.id == id }
        }
    }
}
