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
        PenSkin.entries.forEach { skin ->
            assertTrue("${skin.name} unlocks at ${skin.unlockLevel}, after a pen at $previous", skin.unlockLevel >= previous)
            previous = skin.unlockLevel
        }
    }

    /**
     * Pens deliberately land on the *odd* fives so they never share a level
     * with an AvatarFrame unlock — that is what turns one reward every ten
     * levels into one every five. A pen quietly moved onto a frame level
     * would silently undo the whole point of the second ladder.
     */
    @Test
    fun `no pen ever unlocks on the same level as a frame`() {
        val frameLevels = AvatarFrame.entries.map { it.unlockLevel }.toSet()
        PenSkin.entries.drop(1).forEach { skin ->
            assertTrue(
                "${skin.name} collides with a frame unlock at level ${skin.unlockLevel}",
                skin.unlockLevel !in frameLevels
            )
        }
    }

    @Test
    fun `resolve refuses a pen the level has not earned`() {
        val topPen = PenSkin.entries.last()
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
                "GOLD", "NEON", "LAVA", "GALAXY", "RAINBOW"
            ),
            PenSkin.entries.map { it.name }
        )
    }
}
