package com.sualtikasifi.cizimhafiza.data.bot

import com.sualtikasifi.cizimhafiza.presentation.online.PRESET_EMOJIS
import com.sualtikasifi.cizimhafiza.presentation.online.PRESET_PHRASES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sude has to have something on-topic to say back to everything a player can
 * actually send her. The catch-all pool answering a real message is the bug
 * this guards: "Aynen öyle!" in reply to "Aynen öyle!" is two scripts talking
 * past each other, not a conversation.
 */
class BotChatBrainReplyCoverageTest {

    private val sendableKeys: List<String> =
        PRESET_PHRASES.map { it.key } + PRESET_EMOJIS.map { it.key }

    @Test
    fun `every sendable message has its own reply pool`() {
        val uncovered = sendableKeys.filter { key ->
            BotPersonality.entries.any { personality ->
                BotChatBrain.replyPool(key, personality) === BotChatBrain.GENERIC_REPLY
            }
        }
        assertEquals("No dedicated reply for these incoming keys", emptyList<String>(), uncovered)
    }

    @Test
    fun `no reply pool is empty, in any personality`() {
        for (key in sendableKeys) {
            for (personality in BotPersonality.entries) {
                assertTrue(
                    "Empty reply pool for $key as $personality",
                    BotChatBrain.replyPool(key, personality).isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `she never answers a line with the same line`() {
        // Echoing the incoming phrase back verbatim is the loudest tell there
        // is; a couple of pools legitimately share the topic's own key, so
        // this checks the whole pool rather than one sampled draw.
        for (key in sendableKeys) {
            for (personality in BotPersonality.entries) {
                val pool = BotChatBrain.replyPool(key, personality)
                assertTrue(
                    "$personality can echo $key straight back",
                    pool.none { it.messageKey == key } || pool.size > 1
                )
            }
        }
    }

    @Test
    fun `an unknown key still falls back rather than going silent`() {
        val pool = BotChatBrain.replyPool("chat_this_does_not_exist", BotPersonality.PLAYFUL)
        assertTrue(pool.isNotEmpty())
    }
}
