package com.sualtikasifi.cizimhafiza.data.bot

import kotlin.random.Random

/**
 * Decides *whether*, *what* and *when* the bot (Sude) says something in room
 * 130246 — see [BotRoomEngine] for the Firestore side.
 *
 * Two hard constraints shape everything here:
 *
 * 1. **Silence is the default — but only when nobody is talking to her.** A
 *    bot that comments on every match start and every match end reads as a
 *    script, not a person, so unprompted remarks have deliberately low base
 *    probabilities stacked behind three dampers (personality, mirroring,
 *    per-match budget). A direct reply is the opposite case and skips all
 *    three: ignoring someone who spoke to you is not quietness, it is the
 *    single clearest sign there is nobody there.
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
    /**
     * How often she answers something said TO her. Deliberately near-certain
     * for every character, including QUIET: [chattiness] governs how much
     * someone talks unprompted, and running a direct message through it made
     * a quiet Sude ignore roughly seven greetings out of ten. That does not
     * read as quiet — it reads as broken, or as somebody who has put their
     * phone down. Being quiet means answering briefly, not not answering.
     */
    val replyProbability: Float,
    /**
     * Replies draw on their own, larger allowance than [matchBudget].
     * Spending one pot on both meant a chatty player got two answers and
     * then silence for the rest of the match, which is the same tell from
     * the other direction.
     */
    val replyBudget: Int,
    val weight: Int
) {
    QUIET(chattiness = 0.4f, matchBudget = 2, replyProbability = 0.86f, replyBudget = 6, weight = 35),
    SUPPORTIVE(chattiness = 1.2f, matchBudget = 3, replyProbability = 0.95f, replyBudget = 9, weight = 25),
    COMPETITIVE(chattiness = 1.1f, matchBudget = 3, replyProbability = 0.92f, replyBudget = 8, weight = 20),
    PLAYFUL(chattiness = 1.4f, matchBudget = 4, replyProbability = 0.95f, replyBudget = 10, weight = 20);

    /** Answering and chattering come out of separate allowances — see [replyBudget]. */
    fun budgetFor(moment: BotChatMoment): Int =
        if (moment == BotChatMoment.DIRECT_REPLY) replyBudget else matchBudget

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
enum class BotChatMoment(val baseProbability: Float, val cooldownMs: Long = SLOW_COOLDOWN_MS) {
    // Answering runs on a conversational clock, not the anti-spam one. The
    // shared 30s floor meant a second message sent ten seconds after the
    // first got no reply at all — so a real back-and-forth, which is exactly
    // when a person answers fastest, was the one thing she could not do.
    DIRECT_REPLY(0.75f, cooldownMs = 6_000L),
    MATCH_END_CLOSE(0.40f),
    MATCH_END_HUMAN_GREAT(0.35f),
    MATCH_END_BOT_LOST(0.30f),
    MATCH_END_HUMAN_STRUGGLED(0.30f),
    PLAYER_JOINED(0.25f),
    MATCH_START(0.12f),
    MATCH_END_ROUTINE(0.12f),
    MID_MATCH(0.05f)
}

/** The gap between two unprompted remarks; [BotChatMoment.DIRECT_REPLY] overrides it. */
private const val SLOW_COOLDOWN_MS = 30_000L

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
    // Three lines the catalog has always carried that nothing here ever
    // reached for. "Detaylara bak sen 👀" is the natural answer to being
    // asked for a hint or told nobody can tell what she drew, "Bildin! 🙌"
    // is the natural answer to a correct guess, and "Aklıma geldi!" is what
    // someone actually types mid-round when it clicks.
    private val BILDIN = phrase("chat_bildin")
    private val DETAYLARA_BAK_SEN = phrase("chat_detaylara_bak_sen")
    private val AKLIMA_GELDI = phrase("chat_aklima_geldi")
    private val ANLAMADIM_HIC = phrase("chat_anlamadim_hic")

    /** [PRESET_EMOJIS] keys — an emoji arriving is answered in kind. */
    private val EMOJI_KEYS = setOf("funny", "nice", "hard", "fire", "shock", "hi")

    /**
     * The fallback for an incoming key [replyPool] has no branch for. Named
     * and identity-comparable so BotChatBrainReplyCoverageTest can assert
     * that nothing a player can actually send ever reaches it — adding a
     * phrase to PRESET_PHRASES without a reply for it fails that test rather
     * than quietly shipping "Aynen öyle!" as the answer to everything.
     */
    internal val GENERIC_REPLY = listOf(AYNEN_OYLE, COK_IYIYDI, CLAP, LAUGH)

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
        // A direct reply skips both dampers by design. Mirroring exists to
        // stop her talking into a silent room — a room where someone just
        // spoke to her is the opposite of that — and chattiness is about how
        // much she volunteers, not whether she is rude.
        val isReply = moment == BotChatMoment.DIRECT_REPLY
        val probability = if (isReply) {
            personality.replyProbability
        } else {
            (moment.baseProbability * personality.chattiness * mirror).coerceIn(0f, 0.95f)
        }
        if (random.nextFloat() >= probability) return null

        val fullPool = poolFor(moment, personality, incomingKey)
        if (fullPool.isEmpty()) return null
        val shaped = if (isReply) shapeToRegister(fullPool, incomingKey, personality, random) else fullPool
        val pool = shaped.filterNot { it.messageKey in recentKeys }.ifEmpty { shaped }
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
     * Answer an emoji with an emoji and words with words.
     *
     * Register is most of what makes a reply feel addressed to you rather
     * than merely triggered by you: a 😂 answered with a full sentence reads
     * as a script that did not look at what arrived, and a question answered
     * with a bare 👏 reads as someone who did not read it. QUIET keeps a
     * pull toward the short answer — that is what being quiet actually is —
     * but not an absolute one, because always-emoji is its own tell.
     */
    private fun shapeToRegister(
        pool: List<BotMessage>,
        incomingKey: String?,
        personality: BotPersonality,
        random: Random
    ): List<BotMessage> {
        val wantsEmoji = when {
            incomingKey in EMOJI_KEYS -> true
            personality == BotPersonality.QUIET -> random.nextInt(100) < 60
            else -> false
        }
        val shaped = if (wantsEmoji) {
            pool.filter { it.emoji.isNotEmpty() }
        } else {
            pool.filter { it.emoji.isEmpty() }
        }
        return shaped.ifEmpty { pool }
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
    /**
     * What to say back to a specific incoming line.
     *
     * Every phrase and emoji a player can actually send has an entry here.
     * The catch-all at the bottom used to catch five of them, and answering
     * "Aynen öyle!" with "Aynen öyle!" is not a conversation — it is two
     * scripts talking past each other. On-topic replies are most of what
     * makes an exchange read as a conversation at all, so the mapping is
     * exhaustive on purpose and has to be extended alongside PRESET_PHRASES.
     *
     * Register (emoji vs. words) is decided separately — see
     * [shapeToRegister].
     */
    internal fun replyPool(incomingKey: String?, personality: BotPersonality): List<BotMessage> {
        val pool = when (incomingKey) {
            // --- greetings and openings ---
            "chat_selam", "chat_hos_geldin", "hi" -> listOf(SELAM, HOS_GELDIN, WAVE, HAZIR_MISIN)
            "chat_hazir_misin" -> listOf(BILECEGIM, AYNEN_OYLE, BOL_SANS, FIRE)
            "chat_bol_sans", "chat_iyi_eglenceler" -> listOf(IYI_EGLENCELER, BOL_SANS, CLAP, AYNEN_OYLE)
            "chat_gorusuruz" -> listOf(GORUSURUZ, WAVE, TEKRAR_OYNAYALIM)

            // --- being praised: deflect, don't preen ---
            "chat_harikasin", "chat_super_cizim", "chat_tam_isabet", "nice" ->
                listOf(SWEAT, COK_IYIYDI, AYNEN_OYLE, CLAP)
            "chat_bildin" -> listOf(BILECEGIM, AYNEN_OYLE, CLAP, TAM_ISABET)
            "chat_cok_iyiydi" -> listOf(AYNEN_OYLE, KAFA_KAFAYA, TEKRAR_OYNAYALIM, CLAP)

            // --- her drawing being questioned: the joke is the point ---
            "chat_ne_ciziyorsun" -> listOf(SANAT_BU, DETAYLARA_BAK_SEN, BILECEGIM, LAUGH)
            "chat_ne_bu_oyle", "chat_anlamadim_hic", "funny" ->
                listOf(SANAT_BU, DETAYLARA_BAK_SEN, SWEAT, LAUGH)
            "chat_sanat_bu" -> listOf(AYNEN_OYLE, LAUGH, CLAP)
            "chat_detaylara_bak_sen" -> listOf(ANLAMADIM_HIC, LAUGH, SWEAT)
            "chat_ipucu_ver" -> listOf(DETAYLARA_BAK_SEN, BIR_DAHA_DENE, ZOR_BIR_TANE, LAUGH)

            // --- guessing back and forth ---
            "chat_bilecegim" -> listOf(BIR_DAHA_DENE, ZOR_BIR_TANE, DETAYLARA_BAK_SEN, LAUGH)
            "chat_aklima_geldi", "shock" -> listOf(VAY_CANINA, BILDIN, SHOCK, CLAP)
            "chat_bir_daha_dene" -> listOf(BILECEGIM, SWEAT, LAUGH)
            "chat_az_kaldi" -> listOf(BILECEGIM, AKLIMA_GELDI, SWEAT)
            "chat_zor_bir_tane", "hard" -> listOf(AYNEN_OYLE, ZOR_BIR_TANE, ELINDEN_GELIYOR, SWEAT)
            "chat_vay_canina" -> listOf(AYNEN_OYLE, SUPER_CIZIM, FIRE)

            // --- encouragement received ---
            "chat_elinden_geliyor" -> listOf(AYNEN_OYLE, BILECEGIM, CLAP)

            // --- competitive needling ---
            "chat_yakaladim_seni", "chat_bu_turu_kazanacagim" ->
                listOf(BIR_DAHA_DENE, ROVANS_ISTIYORUM, BILECEGIM, SWEAT)
            "chat_kafa_kafaya", "fire" -> listOf(AYNEN_OYLE, BU_TURU_KAZANACAGIM, VAY_CANINA, FIRE)
            "chat_rovans_istiyorum", "chat_tekrar_oynayalim_mi" ->
                listOf(AYNEN_OYLE, BOL_SANS, BU_TURU_KAZANACAGIM, HAZIR_MISIN)

            // --- agreement is a conversational dead end: move it on ---
            "chat_aynen_oyle" -> listOf(TEKRAR_OYNAYALIM, HAZIR_MISIN, CLAP, LAUGH)

            else -> GENERIC_REPLY
        }

        // Character shows in what she declines to say, not in a separate set
        // of lines: a COMPETITIVE Sude never gushes, a SUPPORTIVE one never
        // needles. Falls back to the unfiltered pool rather than going silent
        // if a topic happens to be made entirely of lines she avoids.
        val avoided = when (personality) {
            BotPersonality.COMPETITIVE -> setOf(HARIKASIN, SUPER_CIZIM, ELINDEN_GELIYOR)
            BotPersonality.SUPPORTIVE -> setOf(BIR_DAHA_DENE, YAKALADIM_SENI, BU_TURU_KAZANACAGIM)
            else -> emptySet()
        }
        return pool.filterNot { it in avoided }.ifEmpty { pool }
    }
}
