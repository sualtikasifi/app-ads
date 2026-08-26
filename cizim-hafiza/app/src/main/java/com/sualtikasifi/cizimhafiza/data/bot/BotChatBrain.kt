package com.sualtikasifi.cizimhafiza.data.bot

import kotlin.random.Random

/**
 * Decides *whether*, *what* and *when* the bot (Sude) says something in room
 * 130246 — see [BotRoomEngine] for the Firestore side.
 *
 * Two hard constraints shape everything here:
 *
 * 1. **Silence is the default.** A bot that comments on every match start and
 *    every match end reads as a script, not a person. Most moments have to
 *    pass without a word, so base probabilities are deliberately low and
 *    stacked behind three dampers (personality, mirroring, per-match budget).
 *
 * 2. **Every decision must be reproducible across devices.** The bot has no
 *    device of its own: its brain runs simultaneously on every present
 *    player's phone. With plain randomness, "20% chance to speak" becomes
 *    ~49% with three devices in the room — the bot would visibly get chattier
 *    the busier the room got. So every roll here is seeded from
 *    (event key + match seed): all devices independently reach the *same*
 *    verdict and pick the *same* message, and a Firestore transaction merely
 *    decides which of them does the writing.
 */

/** One sendable bot message — either a preset phrase ([emoji] blank) or a preset emoji. */
data class BotMessage(val emoji: String, val messageKey: String)

/**
 * Which "person" Sude is right now. Picked per match but sticky across a
 * sitting (see BotRoomEngine.PERSONALITY_TTL_MS): consistent for one evening,
 * someone else tomorrow — a character that re-rolled every single match would
 * be its own kind of tell.
 *
 * [weight] biases the draw toward QUIET, so Sude reads as a mostly-quiet
 * friend who occasionally livens up rather than a permanent chatterbox.
 */
enum class BotPersonality(
    val chattiness: Float,
    val matchBudget: Int,
    val weight: Int
) {
    QUIET(chattiness = 0.4f, matchBudget = 2, weight = 35),
    SUPPORTIVE(chattiness = 1.2f, matchBudget = 3, weight = 25),
    COMPETITIVE(chattiness = 1.1f, matchBudget = 3, weight = 20),
    PLAYFUL(chattiness = 1.4f, matchBudget = 4, weight = 20);

    companion object {
        fun weightedRandom(random: Random): BotPersonality {
            var roll = random.nextInt(entries.sumOf { it.weight })
            for (personality in entries) {
                roll -= personality.weight
                if (roll < 0) return personality
            }
            return QUIET
        }

        fun fromNameOrNull(name: String?): BotPersonality? = entries.find { it.name == name }
    }
}

/**
 * A moment worth possibly reacting to, with the odds Sude says anything at
 * all. Socially obligated moments (someone spoke directly to her) are high;
 * routine game events are low; unprompted chatter is near-zero.
 */
enum class BotChatMoment(val baseProbability: Float) {
    DIRECT_REPLY(0.75f),
    MATCH_END_CLOSE(0.40f),
    MATCH_END_HUMAN_GREAT(0.35f),
    MATCH_END_BOT_LOST(0.30f),
    MATCH_END_HUMAN_STRUGGLED(0.30f),
    PLAYER_JOINED(0.25f),
    MATCH_START(0.12f),
    MATCH_END_ROUTINE(0.12f),
    MID_MATCH(0.05f)
}

/** What [BotChatBrain.decide] returns when Sude has decided to speak. */
data class BotChatDecision(
    val message: BotMessage,
    val delayMs: Long,
    /** Occasionally a second, shorter message right after the first — people do this. */
    val followUp: BotMessage?,
    val followUpDelayMs: Long
)

object BotChatBrain {

    // --- The six preset emojis (keys must match PRESET_EMOJIS in ReactionBar.kt) ---
    private val LAUGH = BotMessage("😂", "funny")
    private val CLAP = BotMessage("👏", "nice")
    private val SWEAT = BotMessage("😅", "hard")
    private val FIRE = BotMessage("🔥", "fire")
    private val SHOCK = BotMessage("😱", "shock")
    private val WAVE = BotMessage("👋", "hi")

    // --- The thirty preset phrases (keys must match PRESET_PHRASES in ReactionBar.kt) ---
    private fun phrase(key: String) = BotMessage("", key)
    private val SELAM = phrase("chat_selam")
    private val HAZIR_MISIN = phrase("chat_hazir_misin")
    private val BOL_SANS = phrase("chat_bol_sans")
    private val IYI_EGLENCELER = phrase("chat_iyi_eglenceler")
    private val HOS_GELDIN = phrase("chat_hos_geldin")
    private val HARIKASIN = phrase("chat_harikasin")
    private val SUPER_CIZIM = phrase("chat_super_cizim")
    private val AYNEN_OYLE = phrase("chat_aynen_oyle")
    private val VAY_CANINA = phrase("chat_vay_canina")
    private val COK_IYIYDI = phrase("chat_cok_iyiydi")
    private val TAM_ISABET = phrase("chat_tam_isabet")
    private val NE_CIZIYORSUN = phrase("chat_ne_ciziyorsun")
    private val AZ_KALDI = phrase("chat_az_kaldi")
    private val ELINDEN_GELIYOR = phrase("chat_elinden_geliyor")
    private val BILECEGIM = phrase("chat_bilecegim")
    private val ZOR_BIR_TANE = phrase("chat_zor_bir_tane")
    private val IPUCU_VER = phrase("chat_ipucu_ver")
    private val SANAT_BU = phrase("chat_sanat_bu")
    private val BIR_DAHA_DENE = phrase("chat_bir_daha_dene")
    private val YAKALADIM_SENI = phrase("chat_yakaladim_seni")
    private val BU_TURU_KAZANACAGIM = phrase("chat_bu_turu_kazanacagim")
    private val ROVANS_ISTIYORUM = phrase("chat_rovans_istiyorum")
    private val KAFA_KAFAYA = phrase("chat_kafa_kafaya")
    private val GORUSURUZ = phrase("chat_gorusuruz")
    private val TEKRAR_OYNAYALIM = phrase("chat_tekrar_oynayalim_mi")

    private val FOLLOW_UP_EMOJIS = listOf(LAUGH, CLAP, FIRE, SWEAT)

    /**
     * The whole decision, from one seed. Returns null when Sude stays quiet —
     * which is most of the time, by design.
     *
     * [recentKeys] are the last few things she said; they're filtered out so
     * the same line never comes back around twice in a row (the single most
     * obvious bot tell).
     */
    fun decide(
        moment: BotChatMoment,
        personality: BotPersonality,
        eventKey: String,
        matchSeed: Long,
        humanMessageCount: Int,
        recentKeys: List<String>,
        incomingKey: String?
    ): BotChatDecision? {
        val random = seededRandom(eventKey, matchSeed)

        // Conversational mirroring: if the player hasn't said a word all
        // match, Sude mostly holds her tongue too. A strong damper rather
        // than a hard gate, so she can still (rarely) open a conversation.
        val mirror = when {
            humanMessageCount <= 0 -> 0.35f
            humanMessageCount <= 2 -> 1.0f
            else -> 1.3f
        }
        val probability = (moment.baseProbability * personality.chattiness * mirror).coerceIn(0f, 0.95f)
        if (random.nextFloat() >= probability) return null

        val fullPool = poolFor(moment, personality, incomingKey)
        if (fullPool.isEmpty()) return null
        val pool = fullPool.filterNot { it.messageKey in recentKeys }.ifEmpty { fullPool }
        val message = pool[random.nextInt(pool.size)]

        // ~8% of the time, tack a quick emoji onto the end — the way people
        // fire off a second thought a beat after the first.
        val wantsFollowUp = random.nextInt(100) < 8
        val followUp = if (wantsFollowUp) {
            FOLLOW_UP_EMOJIS.filterNot { it.messageKey == message.messageKey || it.messageKey in recentKeys }
                .takeIf { it.isNotEmpty() }
                ?.let { it[random.nextInt(it.size)] }
        } else {
            null
        }

        return BotChatDecision(
            message = message,
            delayMs = humanDelayMs(message, random),
            followUp = followUp,
            followUpDelayMs = if (followUp != null) random.nextLong(1_500, 4_001) else 0L
        )
    }

    /**
     * Same seed on every device (see the class doc) — [String.hashCode] is
     * specified by the JDK, so it's stable across devices and app versions.
     */
    private fun seededRandom(eventKey: String, matchSeed: Long): Random =
        Random(eventKey.hashCode().toLong() * 31L + matchSeed)

    /**
     * Reading time, then typing time, and once in a while the pause of
     * someone who put their phone down mid-conversation. An instant reply is
     * the loudest tell of all, so nothing ever lands faster than 1.2s.
     */
    private fun humanDelayMs(message: BotMessage, random: Random): Long {
        val isEmoji = message.emoji.isNotEmpty()
        val reading = if (isEmoji) random.nextLong(600, 1_800) else random.nextLong(800, 2_500)
        val typing = if (isEmoji) random.nextLong(300, 900) else random.nextLong(900, 2_200)
        val distracted = if (random.nextInt(100) < 15) random.nextLong(5_000, 13_000) else 0L
        return (reading + typing + distracted).coerceAtLeast(1_200)
    }

    /**
     * Which lines fit this moment, in this personality's voice. The same
     * thirty phrases carry all four characters — SUPPORTIVE never teases,
     * COMPETITIVE never gushes, PLAYFUL reaches for the jokes, and QUIET
     * mostly answers with a single emoji.
     */
    private fun poolFor(
        moment: BotChatMoment,
        personality: BotPersonality,
        incomingKey: String?
    ): List<BotMessage> = when (moment) {
        BotChatMoment.DIRECT_REPLY -> replyPool(incomingKey, personality)

        BotChatMoment.PLAYER_JOINED -> when (personality) {
            BotPersonality.QUIET -> listOf(WAVE, SELAM)
            BotPersonality.SUPPORTIVE -> listOf(SELAM, HOS_GELDIN, WAVE)
            BotPersonality.COMPETITIVE -> listOf(SELAM, HAZIR_MISIN, HOS_GELDIN)
            BotPersonality.PLAYFUL -> listOf(SELAM, HOS_GELDIN, WAVE, HAZIR_MISIN)
        }

        BotChatMoment.MATCH_START -> when (personality) {
            BotPersonality.QUIET -> listOf(BOL_SANS, CLAP)
            BotPersonality.SUPPORTIVE -> listOf(BOL_SANS, IYI_EGLENCELER, ELINDEN_GELIYOR)
            BotPersonality.COMPETITIVE -> listOf(BU_TURU_KAZANACAGIM, BILECEGIM, FIRE)
            BotPersonality.PLAYFUL -> listOf(IYI_EGLENCELER, BOL_SANS, BILECEGIM)
        }

        BotChatMoment.MID_MATCH -> when (personality) {
            BotPersonality.QUIET -> listOf(SWEAT)
            BotPersonality.SUPPORTIVE -> listOf(AZ_KALDI, ELINDEN_GELIYOR, NE_CIZIYORSUN)
            BotPersonality.COMPETITIVE -> listOf(BILECEGIM, AZ_KALDI, ZOR_BIR_TANE)
            BotPersonality.PLAYFUL -> listOf(NE_CIZIYORSUN, IPUCU_VER, ZOR_BIR_TANE, LAUGH)
        }

        BotChatMoment.MATCH_END_CLOSE -> when (personality) {
            BotPersonality.QUIET -> listOf(VAY_CANINA, FIRE)
            BotPersonality.SUPPORTIVE -> listOf(KAFA_KAFAYA, COK_IYIYDI, VAY_CANINA)
            BotPersonality.COMPETITIVE -> listOf(KAFA_KAFAYA, YAKALADIM_SENI, FIRE)
            BotPersonality.PLAYFUL -> listOf(KAFA_KAFAYA, VAY_CANINA, SHOCK)
        }

        BotChatMoment.MATCH_END_HUMAN_GREAT -> when (personality) {
            BotPersonality.QUIET -> listOf(CLAP, VAY_CANINA)
            BotPersonality.SUPPORTIVE -> listOf(HARIKASIN, COK_IYIYDI, TAM_ISABET, CLAP)
            BotPersonality.COMPETITIVE -> listOf(COK_IYIYDI, VAY_CANINA, ROVANS_ISTIYORUM)
            BotPersonality.PLAYFUL -> listOf(HARIKASIN, VAY_CANINA, SHOCK, CLAP)
        }

        BotChatMoment.MATCH_END_BOT_LOST -> when (personality) {
            BotPersonality.QUIET -> listOf(SWEAT, COK_IYIYDI)
            BotPersonality.SUPPORTIVE -> listOf(COK_IYIYDI, HARIKASIN, TEKRAR_OYNAYALIM)
            BotPersonality.COMPETITIVE -> listOf(ROVANS_ISTIYORUM, TEKRAR_OYNAYALIM, BU_TURU_KAZANACAGIM)
            BotPersonality.PLAYFUL -> listOf(ROVANS_ISTIYORUM, SWEAT, TEKRAR_OYNAYALIM)
        }

        BotChatMoment.MATCH_END_HUMAN_STRUGGLED -> when (personality) {
            BotPersonality.QUIET -> listOf(SWEAT)
            BotPersonality.SUPPORTIVE -> listOf(ELINDEN_GELIYOR, ZOR_BIR_TANE, TEKRAR_OYNAYALIM)
            BotPersonality.COMPETITIVE -> listOf(ZOR_BIR_TANE, BIR_DAHA_DENE, TEKRAR_OYNAYALIM)
            BotPersonality.PLAYFUL -> listOf(ZOR_BIR_TANE, BIR_DAHA_DENE, SWEAT)
        }

        BotChatMoment.MATCH_END_ROUTINE -> when (personality) {
            BotPersonality.QUIET -> listOf(CLAP)
            BotPersonality.SUPPORTIVE -> listOf(COK_IYIYDI, IYI_EGLENCELER, CLAP)
            BotPersonality.COMPETITIVE -> listOf(TEKRAR_OYNAYALIM, ROVANS_ISTIYORUM)
            BotPersonality.PLAYFUL -> listOf(TEKRAR_OYNAYALIM, SANAT_BU, LAUGH)
        }
    }

    /**
     * What to say back to a specific incoming line. Keeps replies on-topic —
     * a greeting gets greeted, a jab at her drawing gets "Sanat bu! 🎨" —
     * which is most of what makes an exchange read as a conversation rather
     * than two independent broadcasts.
     *
     * QUIET answers with an emoji wherever the pool offers one.
     */
    private fun replyPool(incomingKey: String?, personality: BotPersonality): List<BotMessage> {
        val pool = when (incomingKey) {
            "chat_selam", "chat_hos_geldin", "hi" -> listOf(SELAM, HOS_GELDIN, WAVE)
            "chat_hazir_misin" -> listOf(BILECEGIM, IYI_EGLENCELER, BOL_SANS, CLAP)
            "chat_bol_sans", "chat_iyi_eglenceler" -> listOf(IYI_EGLENCELER, BOL_SANS, CLAP)
            "chat_harikasin", "chat_super_cizim", "chat_cok_iyiydi", "chat_tam_isabet", "chat_bildin" ->
                listOf(AYNEN_OYLE, SWEAT, CLAP, COK_IYIYDI)
            "chat_ne_ciziyorsun" -> listOf(SANAT_BU, LAUGH, BILECEGIM)
            "chat_ne_bu_oyle", "chat_anlamadim_hic", "funny" -> listOf(SANAT_BU, LAUGH, SWEAT)
            "chat_sanat_bu" -> listOf(AYNEN_OYLE, LAUGH, CLAP)
            "chat_ipucu_ver" -> listOf(BIR_DAHA_DENE, LAUGH, ZOR_BIR_TANE)
            "chat_bir_daha_dene" -> listOf(SWEAT, LAUGH, ROVANS_ISTIYORUM)
            "chat_rovans_istiyorum", "chat_tekrar_oynayalim_mi" ->
                listOf(AYNEN_OYLE, BOL_SANS, BU_TURU_KAZANACAGIM)
            "chat_gorusuruz" -> listOf(GORUSURUZ, WAVE)
            "chat_yakaladim_seni", "chat_bu_turu_kazanacagim" -> listOf(ROVANS_ISTIYORUM, BILECEGIM, SWEAT)
            "chat_kafa_kafaya", "fire" -> listOf(AYNEN_OYLE, FIRE, VAY_CANINA)
            "chat_zor_bir_tane", "hard" -> listOf(AYNEN_OYLE, SWEAT, ELINDEN_GELIYOR)
            "chat_bilecegim" -> listOf(BIR_DAHA_DENE, ZOR_BIR_TANE, LAUGH)
            "chat_aklima_geldi", "shock" -> listOf(VAY_CANINA, SHOCK, CLAP)
            "nice" -> listOf(CLAP, AYNEN_OYLE, SWEAT)
            else -> listOf(AYNEN_OYLE, CLAP, LAUGH)
        }
        return if (personality == BotPersonality.QUIET) {
            pool.filter { it.emoji.isNotEmpty() }.ifEmpty { pool }
        } else {
            pool
        }
    }
}
