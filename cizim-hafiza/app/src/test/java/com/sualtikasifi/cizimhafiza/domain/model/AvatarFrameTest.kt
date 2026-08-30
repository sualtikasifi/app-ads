package com.sualtikasifi.cizimhafiza.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarFrameTest {

    @Test
    fun `the default frame is available from level one`() {
        assertEquals(1, AvatarFrame.DEFAULT.unlockLevel)
        assertTrue(AvatarFrame.unlockedFor(1).contains(AvatarFrame.DEFAULT))
    }

    @Test
    fun `frames are declared in ascending unlock order`() {
        // highestUnlockedFor uses entries.last { … }, which is only correct
        // while the declaration order matches the unlock order.
        var previous = 0
        AvatarFrame.entries.forEach { frame ->
            assertTrue(
                "${frame.name} unlocks at ${frame.unlockLevel}, after a frame at $previous",
                frame.unlockLevel >= previous
            )
            previous = frame.unlockLevel
        }
    }

    @Test
    fun `a new frame arrives every ten levels up to the cap`() {
        // The product rule this ladder was built to: one unlock per 10
        // levels, with the final frame landing exactly at MAX_LEVEL.
        assertEquals(11, AvatarFrame.entries.size)
        assertEquals(PlayerLevel.MAX_LEVEL, AvatarFrame.entries.last().unlockLevel)
        AvatarFrame.entries.drop(1).forEachIndexed { index, frame ->
            assertEquals("frame ${index + 2}", (index + 1) * 10, frame.unlockLevel)
        }
    }

    @Test
    fun `unlockedFor grows monotonically with level`() {
        var previousCount = 0
        for (level in 1..PlayerLevel.MAX_LEVEL) {
            val count = AvatarFrame.unlockedFor(level).size
            assertTrue("level $level unlocked fewer frames than level ${level - 1}", count >= previousCount)
            previousCount = count
        }
        assertEquals(AvatarFrame.entries.size, AvatarFrame.unlockedFor(PlayerLevel.MAX_LEVEL).size)
    }

    @Test
    fun `resolve keeps a legitimately unlocked pick`() {
        assertEquals(
            AvatarFrame.ARTIST,
            AvatarFrame.resolve(AvatarFrame.ARTIST.name, level = 50)
        )
    }

    @Test
    fun `resolve refuses a frame the level has not earned`() {
        // The selection is persisted as a plain string in SharedPreferences,
        // so it must not be trusted to still be legal — a restored backup or
        // an edited prefs file could name a frame this level cannot wear.
        val topFrame = AvatarFrame.entries.last()
        assertEquals(AvatarFrame.DEFAULT, AvatarFrame.resolve(topFrame.name, level = 1))
    }

    @Test
    fun `resolve falls back cleanly on missing or unknown names`() {
        assertEquals(AvatarFrame.DEFAULT, AvatarFrame.resolve(null, level = 100))
        assertEquals(AvatarFrame.DEFAULT, AvatarFrame.resolve("", level = 100))
        // A constant renamed in a future version would strand old prefs on a
        // name that no longer resolves; that must degrade, not crash.
        assertEquals(AvatarFrame.DEFAULT, AvatarFrame.resolve("FRAME_FROM_A_FUTURE_VERSION", level = 100))
    }

    @Test
    fun `highestUnlockedFor never returns a locked frame`() {
        for (level in 1..PlayerLevel.MAX_LEVEL) {
            val frame = AvatarFrame.highestUnlockedFor(level)
            assertTrue("level $level got ${frame.name}", level >= frame.unlockLevel)
        }
    }

    @Test
    fun `every frame has a distinct drawable and a sane face geometry`() {
        val drawables = AvatarFrame.entries.map { it.drawableRes }
        assertEquals("two frames share the same artwork", drawables.size, drawables.toSet().size)
        AvatarFrame.entries.forEach { frame ->
            assertTrue("${frame.name} face too small", frame.faceDiameterFraction > 0.2f)
            assertTrue("${frame.name} face too large", frame.faceDiameterFraction < 1f)
            // The offsets nudge the number into a hole that is slightly off
            // centre; anything larger than this would push it off the frame.
            assertTrue("${frame.name} x offset", kotlin.math.abs(frame.faceOffsetXFraction) < 0.1f)
            assertTrue("${frame.name} y offset", kotlin.math.abs(frame.faceOffsetYFraction) < 0.1f)
        }
    }

    @Test
    fun `enum names are stable identifiers`() {
        // These are persisted verbatim (SettingsRepository.KEY_SELECTED_AVATAR_FRAME
        // and OnlinePlayer.frameId), so renaming one silently resets every
        // player who wore it. Pinning the set here makes that a failing test
        // rather than a support ticket.
        assertEquals(
            listOf(
                "SCRIBBLER", "ARTIST", "APPRENTICE", "POP_ART", "WOOD_PALETTE",
                "MASTER_PAINTER", "WATERCOLOR_BRUSHES", "PAINTER", "CHALK",
                "GRAFFITI", "GRAND_MASTER"
            ),
            AvatarFrame.entries.map { it.name }
        )
    }

    @Test
    fun `a locked frame is not offered in the picker`() {
        val unlockedAt35 = AvatarFrame.unlockedFor(35)
        assertTrue(unlockedAt35.all { it.unlockLevel <= 35 })
        assertFalse(unlockedAt35.contains(AvatarFrame.entries.last()))
    }
}
