package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenSkinTest {

    @Test
    fun `the default pen is available from level one`() {
        assertEquals(1, PenSkin.DEFAULT.unlockLevel)
        assertTrue(PenSkin.unlockedFor(1).contains(PenSkin.DEFAULT))
    }

    @Test
    fun `pens are declared in ascending unlock order`() {
        var previous = 0
        PenSkin.ladder.forEach { skin ->
            assertTrue("${skin.name} unlocks at ${skin.unlockLevel}, after a pen at $previous", skin.unlockLevel >= previous)
            previous = skin.unlockLevel
        }
    }

    /**
     * League prizes must stay off the level ladder entirely. They carry
     * unlockLevel 0 so [PenSkin.resolve] will render one on any player
     * already wearing it (including an opponent, whose winnings this device
     * cannot know) — which means the ONLY thing keeping them from being
     * handed to everyone at level one is the isLeagueReward filter.
     */
    @Test
    fun `league pens are never unlocked by levelling`() {
        assertTrue("no league pens declared", PenSkin.entries.any { it.isLeagueReward })
        for (level in 1..PlayerLevel.MAX_LEVEL) {
            assertTrue(
                "a league pen appeared on the ladder at level $level",
                PenSkin.unlockedFor(level).none { it.isLeagueReward }
            )
        }
    }

    @Test
    fun `resolve renders a league pen at any level`() {
        val leaguePen = PenSkin.entries.first { it.isLeagueReward }
        assertEquals(leaguePen, PenSkin.resolve(leaguePen.name, level = 1))
    }

    /**
     * Pens deliberately land on the *odd* fives so they never share a level
     * with an AvatarFrame unlock — that is what turns one reward every ten
     * levels into one every five. A pen quietly moved onto a frame level
     * would silently undo the whole point of the second ladder.
     */
    @Test
    fun `no pen ever unlocks on the same level as a frame`() {
        val frameLevels = AvatarFrame.entries.filter { !it.isLeagueReward }.map { it.unlockLevel }.toSet()
        PenSkin.ladder.drop(1).forEach { skin ->
            assertTrue(
                "${skin.name} collides with a frame unlock at level ${skin.unlockLevel}",
                skin.unlockLevel !in frameLevels
            )
        }
    }

    @Test
    fun `resolve refuses a pen the level has not earned`() {
        val topPen = PenSkin.ladder.last()
        assertEquals(PenSkin.DEFAULT, PenSkin.resolve(topPen.name, level = 1))
        assertEquals(topPen, PenSkin.resolve(topPen.name, level = PlayerLevel.MAX_LEVEL))
    }

    @Test
    fun `resolve falls back cleanly on missing or unknown names`() {
        assertEquals(PenSkin.DEFAULT, PenSkin.resolve(null, level = 100))
        assertEquals(PenSkin.DEFAULT, PenSkin.resolve("PEN_FROM_A_FUTURE_VERSION", level = 100))
    }

    @Test
    fun `every pen has at least one colour and a distinct label`() {
        PenSkin.entries.forEach { skin ->
            assertTrue("${skin.name} has no colour", skin.colors.isNotEmpty())
            skin.colors.forEach { argb ->
                // Fully opaque: a half-transparent stroke would let the paper
                // texture bleed through and read as a rendering bug.
                assertEquals("${skin.name} colour is not opaque", 0xFF, ((argb shr 24) and 0xFF).toInt())
            }
        }
        val labels = PenSkin.entries.map { it.labelRes }
        assertEquals("two pens share a label", labels.size, labels.toSet().size)
    }

    @Test
    fun `isGradient matches the colour count`() {
        PenSkin.entries.forEach { skin ->
            assertEquals(skin.colors.size > 1, skin.isGradient)
        }
    }

    @Test
    fun `enum names are stable identifiers`() {
        // Persisted verbatim in SettingsRepository.KEY_SELECTED_PEN_SKIN, so
        // a rename silently resets every player wearing that pen.
        assertEquals(
            listOf(
                "CLASSIC", "CHARCOAL", "OCEAN", "SUNSET", "FOREST", "BERRY",
                "GOLD", "NEON", "LAVA", "GALAXY", "RAINBOW",
                // League prizes. Added deliberately — the guard is against a
                // RENAME, which resets every player wearing that pen; a new
                // entry is meant to be an explicit edit here too.
                "LEAGUE_AURORA", "LEAGUE_EMBER", "LEAGUE_FROST",
                "LEAGUE_MIDNIGHT", "LEAGUE_CITRUS", "LEAGUE_ROSE"
            ),
            PenSkin.entries.map { it.name }
        )
    }
}
